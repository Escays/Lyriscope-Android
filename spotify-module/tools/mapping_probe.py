#!/usr/bin/env python3
"""Locate Spotify's obfuscated anchors inside an APK, without jadx or apktool.

Spotify ships R8-renamed classes, so every release needs a new row in
SpotifyMapping.java. Finding the new names by hand is the slow part of that job:
browse a decompiled tree, guess which `p.xxxx` is the card renderer, and hope it
is the right branch of an R8-merged class.

This tool reads the APK's DEX files directly and answers the questions you would
otherwise ask a decompiler:

    # Every class carrying a known resource key or literal.
    python mapping_probe.py spotify.apk strings npv_lyrics_toggle

    # Structural search: any method whose shape matches, names ignored.
    python mapping_probe.py spotify.apk shape "static void(*,*,*,*,*,*,*,*,*,int)"

    # The presets that correspond to SpotifyMapping's groups.
    python mapping_probe.py spotify.apk anchors
    python mapping_probe.py spotify.apk anchor card-renderer

Shapes are obfuscation-proof because R8 keeps arity, parameter order and return
types. Names are not. Results are candidates: confirm each one against the
behaviour recorded in INLINE_MAPPING.md, then paste it into SpotifyMapping.java.

  python mapping_probe.py --selftest ../build/Lyriscope-Spotify-1.0.7.apk
"""
import argparse
import struct
import sys
import zipfile
from pathlib import Path

PRIMITIVES = {"void": "V", "boolean": "Z", "byte": "B", "short": "S", "char": "C",
              "int": "I", "long": "J", "float": "F", "double": "D"}
ALIASES = {
    "ContextTrack": "Lcom/spotify/player/model/ContextTrack;",
    "String": "Ljava/lang/String;",
    "Object": "Ljava/lang/Object;",
    "List": "Ljava/util/List;",
    "Set": "Ljava/util/Set;",
    "ArrayList": "Ljava/util/ArrayList;",
    "Map": "Ljava/util/Map;",
    "Maybe": "Lio/reactivex/rxjava3/core/Maybe;",
    "Observable": "Lio/reactivex/rxjava3/core/Observable;",
    "Flowable": "Lio/reactivex/rxjava3/core/Flowable;",
    "Function1": "Lkotlin/jvm/functions/Function1;",
    "Any": "*",
}


def descriptor(token):
    token = token.strip()
    if token in PRIMITIVES:
        return PRIMITIVES[token]
    if token in ALIASES:
        return ALIASES[token]
    if token.startswith("L") and token.endswith(";"):
        return token
    if token.startswith("*"):
        return "*"
    return "L" + token.replace(".", "/") + ";"


class Shape:
    """Access, return type and parameter list with `*` wildcards."""

    def __init__(self, text):
        text = text.strip()
        self.access, rest = text.split(" ", 1)
        self.returns, params = rest.split("(", 1)
        self.returns = descriptor(self.returns)
        body = params.rstrip(")").strip()
        self.params = [] if not body else [descriptor(part) for part in body.split(",")]
        if self.access in ("static", "instance", "ctor"):
            pass
        elif self.access == "any":
            pass
        else:
            raise ValueError("access must be static, instance, ctor or any")

    def matches(self, method):
        if self.access == "static" and not method.is_static:
            return False
        if self.access == "instance" and (method.is_static or method.is_ctor):
            return False
        if self.access == "ctor" and not method.is_ctor:
            return False
        if self.returns != "*" and method.returns != self.returns:
            return False
        if len(self.params) != len(method.params):
            return False
        for expected, actual in zip(self.params, method.params):
            if expected != "*" and expected != actual:
                return False
        return True


class Method:
    __slots__ = ("owner", "name", "params", "returns", "access", "code_off", "is_ctor")

    def __init__(self, owner, name, params, returns, access, code_off):
        self.owner = owner
        self.name = name
        self.params = params
        self.returns = returns
        self.access = access
        self.code_off = code_off
        self.is_ctor = name in ("<init>", "<clinit>")

    @property
    def is_static(self):
        return bool(self.access & 0x8)

    def signature(self):
        return "%s %s(%s)" % (self.returns, self.name, ",".join(self.params))


class Dex:
    """Minimal DEX reader: strings, types, protos, methods, fields, class data, code strings."""

    def __init__(self, data):
        self.data = data
        (self.string_ids_size, self.string_ids_off,
         self.type_ids_size, self.type_ids_off,
         self.proto_ids_size, self.proto_ids_off,
         self.field_ids_size, self.field_ids_off,
         self.method_ids_size, self.method_ids_off,
         self.class_defs_size, self.class_defs_off) = struct.unpack_from("<12I", data, 56)
        self._strings = [None] * self.string_ids_size
        self._types = [None] * self.type_ids_size
        self._protos = [None] * self.proto_ids_size

    def string(self, index):
        cached = self._strings[index]
        if cached is not None:
            return cached
        offset = struct.unpack_from("<I", self.data, self.string_ids_off + index * 4)[0]
        size, cursor = uleb(self.data, offset)
        end = self.data.index(b"\x00", cursor)
        value = bytes(self.data[cursor:end]).decode("utf-8", "replace")
        self._strings[index] = value
        return value

    def type(self, index):
        cached = self._types[index]
        if cached is not None:
            return cached
        value = self.string(struct.unpack_from("<I", self.data, self.type_ids_off + index * 4)[0])
        self._types[index] = value
        return value

    def proto(self, index):
        cached = self._protos[index]
        if cached is not None:
            return cached
        returns_idx, params_off = struct.unpack_from(
            "<II", self.data, self.proto_ids_off + index * 12 + 4)
        params = []
        if params_off:
            count = struct.unpack_from("<I", self.data, params_off)[0]
            params = [self.type(struct.unpack_from("<H", self.data, params_off + 4 + i * 2)[0])
                      for i in range(count)]
        value = (self.type(returns_idx), params)
        self._protos[index] = value
        return value

    def method_id(self, index):
        class_idx, proto_idx, name_idx = struct.unpack_from(
            "<HHI", self.data, self.method_ids_off + index * 8)
        returns, params = self.proto(proto_idx)
        return self.type(class_idx), self.string(name_idx), params, returns

    def field_id(self, index):
        class_idx, type_idx, name_idx = struct.unpack_from(
            "<HHI", self.data, self.field_ids_off + index * 8)
        return self.type(class_idx), self.string(name_idx), self.type(type_idx)

    def classes(self):
        """Yield (descriptor, superclass, [Method], [field names])."""
        for index in range(self.class_defs_size):
            base = self.class_defs_off + index * 32
            class_idx, _flags, super_idx, _ifaces, _source, _annotations, data_off, _values = \
                struct.unpack_from("<8I", self.data, base)
            name = self.type(class_idx)
            superclass = self.type(super_idx) if super_idx != 0xFFFFFFFF else None
            methods, fields = [], []
            if data_off:
                methods, fields = self.class_data(name, data_off)
            yield name, superclass, methods, fields

    def class_interfaces(self):
        """Yield (class, superclass, [direct interfaces]) without reading class_data."""
        for index in range(self.class_defs_size):
            base = self.class_defs_off + index * 32
            class_idx, _flags, super_idx, ifaces_off = struct.unpack_from("<4I", self.data, base)
            yield (self.type(class_idx),
                   None if super_idx == 0xFFFFFFFF else self.type(super_idx),
                   self.interfaces(ifaces_off))

    def interfaces(self, offset):
        if not offset:
            return []
        count = struct.unpack_from("<I", self.data, offset)[0]
        return [self.type(struct.unpack_from("<H", self.data, offset + 4 + i * 2)[0])
                for i in range(count)]

    def class_data(self, owner, offset):
        counts = []
        cursor = offset
        for _ in range(4):
            value, cursor = uleb(self.data, cursor)
            counts.append(value)
        static_fields, instance_fields, direct_methods, virtual_methods = counts
        fields = []
        for total, is_static in ((static_fields, True), (instance_fields, False)):
            field_idx = 0
            for _ in range(total):
                diff, cursor = uleb(self.data, cursor)
                _flags, cursor = uleb(self.data, cursor)
                field_idx += diff
                _, name, field_type = self.field_id(field_idx)
                # The type is what identifies a branch flag (`a:I`) or its payload (`b:Ljava/lang/Object;`).
                fields.append(("static " if is_static else "") + name + ":" + describe(field_type))
        methods = []
        for total, is_static in ((direct_methods, True), (virtual_methods, False)):
            method_idx = 0
            for _ in range(total):
                diff, cursor = uleb(self.data, cursor)
                flags, cursor = uleb(self.data, cursor)
                code_off, cursor = uleb(self.data, cursor)
                method_idx += diff
                _owner, name, params, returns = self.method_id(method_idx)
                methods.append(Method(owner, name, params, returns, flags, code_off))
        return methods, fields

    def code_strings(self, code_off):
        """String constants referenced by one method body. Payload records are skipped whole."""
        result = set()
        try:
            insns_size = struct.unpack_from("<I", self.data, code_off + 12)[0]
            start = code_off + 16
            cursor = 0
            while cursor < insns_size:
                unit = struct.unpack_from("<H", self.data, start + cursor * 2)[0]
                opcode = unit & 0xFF
                if opcode == 0x00:
                    cursor += payload_width(self.data, start + cursor * 2, unit)
                elif opcode == 0x1A:
                    index = struct.unpack_from("<H", self.data, start + (cursor + 1) * 2)[0]
                    if index < self.string_ids_size:
                        result.add(self.string(index))
                    cursor += 2
                elif opcode == 0x1B:
                    index = struct.unpack_from("<I", self.data, start + (cursor + 1) * 2)[0]
                    if index < self.string_ids_size:
                        result.add(self.string(index))
                    cursor += 3
                else:
                    cursor += instruction_width(opcode)
        except (struct.error, IndexError, ValueError):
            return result
        return result


WIDTH_1 = ({0x00, 0x01, 0x04, 0x07, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11,
            0x12, 0x1D, 0x1E, 0x21, 0x27, 0x78}
           | set(range(0x7B, 0x90)) | set(range(0xB0, 0xD0)) | set(range(0xE3, 0x100)))
WIDTH_2 = ({0x02, 0x05, 0x08, 0x13, 0x15, 0x16, 0x19, 0x1A, 0x1C, 0x1F, 0x20, 0x22, 0x23,
            0x29, 0xFE, 0xFF}
           | set(range(0x2D, 0x32)) | set(range(0x32, 0x3E)) | set(range(0x44, 0x52))
           | set(range(0x52, 0x60)) | set(range(0x60, 0x6E)) | set(range(0x90, 0xB0)))
WIDTH_3 = ({0x03, 0x06, 0x09, 0x14, 0x17, 0x1B, 0x24, 0x25, 0x26, 0x2A, 0x2B, 0x2C, 0xFC, 0xFD}
           | set(range(0x6E, 0x78)))
WIDTH_4 = {0xFA, 0xFB}
WIDTH_5 = {0x18}


def instruction_width(opcode):
    if opcode in WIDTH_1:
        return 1
    if opcode in WIDTH_2:
        return 2
    if opcode in WIDTH_3:
        return 3
    if opcode in WIDTH_4:
        return 4
    if opcode in WIDTH_5:
        return 5
    return 1


def payload_width(data, offset, ident):
    """Words occupied by a packed/sparse switch or fill-array payload record."""
    kind = ident & 0xFF00
    if kind == 0x0100:
        size = struct.unpack_from("<H", data, offset + 2)[0]
        return 2 + size * 2
    if kind == 0x0200:
        size = struct.unpack_from("<H", data, offset + 2)[0]
        return 2 + size * 4
    if kind == 0x0300:
        width = struct.unpack_from("<H", data, offset + 2)[0]
        size = struct.unpack_from("<I", data, offset + 4)[0]
        return 4 + (size * width + 1) // 2
    return 1


def uleb(data, offset):
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, offset
        shift += 7


def describe(descriptor):
    """Readable name for a DEX field descriptor, so branch flags stay distinguishable."""
    if descriptor.startswith("L"):
        return descriptor[1:-1].replace("/", ".")
    if descriptor.startswith("["):
        return describe(descriptor[1:]) + "[]"
    return {"I": "int", "Z": "boolean", "F": "float", "J": "long", "B": "byte", "S": "short",
            "C": "char", "D": "double", "V": "void"}.get(descriptor, descriptor)


class Index:
    """All classes of an APK, with lazily scanned string constants."""
    def __init__(self, apk, scan_strings=True):
        self.classes = []
        self.strings = {}
        self.parents = {}
        self.dexes = []
        with zipfile.ZipFile(apk) as archive:
            names = sorted((n for n in archive.namelist()
                            if n.startswith("classes") and n.endswith(".dex")),
                           key=lambda n: (len(n), n))
            for name in names:
                dex = Dex(archive.read(name))
                self.dexes.append(dex)
                for descriptor, superclass, interfaces in dex.class_interfaces():
                    self.parents[descriptor] = (superclass, interfaces)
                for descriptor, superclass, methods, fields in dex.classes():
                    self.classes.append((descriptor, superclass, methods, fields))
                    if scan_strings:
                        found = set()
                        for method in methods:
                            if method.code_off:
                                found |= dex.code_strings(method.code_off)
                        self.strings[descriptor] = found

    def simple(self, descriptor):
        name = descriptor[1:-1] if descriptor.startswith("L") else descriptor
        return name.replace("/", ".")

    def find_classes(self, pattern):
        compact = pattern.replace(".", "/")
        return [c for c in self.classes
                if compact in c[0] or self.simple(c[0]).split("$")[-1] == pattern]

    def by_string(self, text):
        hits = [c for c in self.classes if text in self.strings.get(c[0], ())]
        return sorted(hits, key=lambda c: (len(c[0]), c[0]))

    def static_fields(self, type_name, allow=None):
        """Classes holding a static field of exactly this type, as (class, field name).

        Singletons such as `Modifier` and Kotlin's `Unit` are only reachable through the
        static field that holds them, so the field type is the anchor.
        """
        hits = []
        for descriptor, _superclass, _methods, fields in self.classes:
            if allow is not None and not allow(descriptor):
                continue
            for field in fields:
                if not field.startswith("static "):
                    continue
                name, _, field_type = field[len("static "):].partition(":")
                if field_type == type_name:
                    hits.append((descriptor, name))
        return sorted(hits, key=lambda hit: (len(hit[0]), hit[0]))

    def singleton_fields(self, allow=None):
        """Kotlin objects: classes holding a static field of their own type."""
        hits = []
        for descriptor, _superclass, _methods, fields in self.classes:
            if allow is not None and not allow(descriptor):
                continue
            simple = self.simple(descriptor)
            for field in fields:
                if not field.startswith("static "):
                    continue
                name, _, field_type = field[len("static "):].partition(":")
                if field_type == simple:
                    hits.append((descriptor, name))
        return sorted(hits, key=lambda hit: (len(hit[0]), hit[0]))

    def method_references(self, text, allow=None):
        """(class, method) pairs whose own body loads this literal.

        A Compose file compiles to one class holding many composables, so a class-level
        literal only narrows the field to a few dozen candidates. Which *method* sets a
        semantics tag or reads a preference is what identifies the anchor.
        """
        wanted = {c[0] for c in self.classes if text in self.strings.get(c[0], ())}
        hits = []
        for dex in self.dexes:
            for descriptor, _superclass, methods, _fields in dex.classes():
                if descriptor not in wanted:
                    continue
                if allow is not None and not allow(descriptor):
                    continue
                for method in methods:
                    if method.code_off and text in dex.code_strings(method.code_off):
                        hits.append((descriptor, method))
        return sorted(hits, key=lambda hit: (len(hit[0]), hit[0], hit[1].name))

    def composer_tally(self, allow=None):
        """Rank likely Compose `Composer` types.

        Every composable compiles to `static void f(args…, Composer, int, int?)`, so the
        type sitting before the trailing flag ints shows up hundreds of times. R8 moves
        Compose runtime into the same package as the app, so the winner is the Composer.
        """
        tally = {}
        for descriptor, _superclass, methods, _fields in self.classes:
            if allow is not None and not allow(descriptor):
                continue
            for method in methods:
                if not method.is_static or method.returns != "V" or len(method.params) < 3:
                    continue
                if method.params[-1] != "I":
                    continue
                for position in (-2, -3):
                    candidate = method.params[position]
                    if candidate.startswith("Lp/") or candidate.startswith("Landroidx/"):
                        tally[candidate] = tally.get(candidate, 0) + 1
        return sorted(tally.items(), key=lambda pair: (-pair[1], pair[0]))

    def by_shape(self, shape, allow=None):
        hits = []
        for descriptor, _superclass, methods, _fields in self.classes:
            if allow is not None and not allow(descriptor):
                continue
            for method in methods:
                if is_noise(method.name):
                    continue
                if shape.matches(method):
                    hits.append((descriptor, method))
                    break
        return sorted(hits, key=lambda pair: (len(pair[0]), pair[0]))


def is_noise(name):
    """Compiler-generated bridges never own a mapping; matching them only adds rows."""
    return ("$r8$lambda$" in name or name.startswith("lambda$")
            or "$$Lambda$" in name or name.startswith("access$"))


ANCHORS = {
    "card-tag": ("Semantics tag only the native lyrics card renderer sets", "string",
                 "#LyricsCardTag"),
    "card-renderer": ("Static Now Playing card renderer: (…9 args…, int)", "shape",
                      "static void(*,*,*,*,*,*,*,*,*,int)"),
    "compose-interop": ("Host Compose AndroidView slot: (Function1, ?, Function1, ?, int, int)",
                        "shape", "static void(Function1,*,Function1,*,int,int)"),
    "inline-line": ("Only the inline lyric line sets this semantics tag", "string", "lyrics_line"),
    "availability": ("Card availability provider: Maybe(ContextTrack, String)",
                     "shape", "any Maybe(ContextTrack,String)"),
    "availability-old": ("Merged-branch availability: Maybe(ContextTrack)",
                         "shape", "any Maybe(ContextTrack)"),
    "presence-event": ("Scroll-section event carrying the card: (ContextTrack, String, List, List, ?)",
                       "shape", "ctor void(ContextTrack,String,List,List,*)"),
    "presence-event-old": ("Scroll-section event without a category: (ContextTrack, String, List)",
                           "shape", "ctor void(ContextTrack,String,List)"),
    "order-filter": ("Card order filter: static ArrayList(?, Set)", "shape",
                     "static ArrayList(*,Set)"),
    "track-gate": ("Second menu gate: static boolean(ContextTrack)", "shape",
                   "static boolean(ContextTrack)"),
    "inline-renderer": ("Inline lyric line renderer: void(?,?,?,?,?)", "shape",
                        "instance void(*,*,*,*,*)"),
    "metadata-client": ("TrackV4 metadata client method: Observable(?)", "shape",
                        "any Observable(*)"),
    "inline-preference": ("Inline line visibility preference key", "string",
                          "key_lyrics_on_npv_visible"),
    "inline-toggle": ("Native Lyrics On/Off menu toggle", "string", "npv_lyrics_toggle"),
    "lyrics-flag": ("Track metadata flag used by the native menu gate", "string", "has_lyrics"),
    "track-prefix": ("Song URI prefix that the menu gate accepts", "string", "spotify:track:"),
}


def report(hits, limit, show_signature=True):
    if not hits:
        print("  (no match)")
        return
    for index, hit in enumerate(hits[:limit]):
        descriptor = hit[0] if isinstance(hit, tuple) else hit
        name = descriptor[1:-1].replace("/", ".") if descriptor.startswith("L") else descriptor
        if isinstance(hit, tuple) and len(hit) > 1 and isinstance(hit[1], Method):
            method = hit[1]
            print("  %-40s %s %s(%s)" % (name, method.returns, method.name,
                                         ",".join(method.params)))
        else:
            print("  %s" % name)
    if len(hits) > limit:
        print("  … %d more" % (len(hits) - limit))


def main(argv):
    parser = argparse.ArgumentParser(description="Find Spotify anchor classes in an APK.")
    parser.add_argument("--apk", help="Spotify APK (or any APK to inspect)")
    parser.add_argument("--limit", type=int, default=12, help="max rows per query")
    parser.add_argument("--no-strings", action="store_true",
                        help="skip code scanning; much faster, disables string queries")
    parser.add_argument("--pkg", help="only classes whose name contains this substring")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("anchors", help="list every preset and its expected shape")
    run_anchor = sub.add_parser("anchor", help="run one preset")
    run_anchor.add_argument("name", choices=sorted(ANCHORS))
    run_shape = sub.add_parser("shape", help='run a raw shape query, e.g. "static void(*,int)"')
    run_shape.add_argument("pattern")
    run_strings = sub.add_parser("strings", help="classes referencing a literal")
    run_strings.add_argument("text")
    run_refs = sub.add_parser("refs", help="methods whose own body references a literal")
    run_refs.add_argument("text")
    run_statics = sub.add_parser("statics", help="classes with a static field of this type")
    run_statics.add_argument("type")
    sub.add_parser("singletons", help="classes holding a static field of their own type")
    run_class = sub.add_parser("class", help="methods and fields of one class")
    run_class.add_argument("name")
    sub.add_parser("composer", help="rank likely Compose Composer types by frequency")
    run_card = sub.add_parser("compose-card",
                              help="card renderer: static void with 9 args, Composer, int")
    run_card.add_argument("composer", help="Composer type, e.g. p.ryz")
    sub.add_parser("selftest", help="verify the DEX reader against a known APK")
    args = parser.parse_args(argv)

    if args.command == "anchors":
        for name, (description, kind, query) in sorted(ANCHORS.items()):
            print("%-20s %s\n%-20s %s %s" % (name, description, "", kind, query))
        return 0

    if not args.apk:
        parser.error("--apk is required")
    if not args.command:
        parser.print_help()
        return 2

    index = Index(args.apk, scan_strings=not args.no_strings)
    allow = None
    if args.pkg:
        needle = args.pkg.replace(".", "/")
        allow = lambda descriptor: needle in descriptor

    if args.command == "selftest":
        return selftest(index)

    if args.command == "anchor":
        description, kind, query = ANCHORS[args.name]
        print("%s\n%s: %s\n" % (description, kind, query))
        hits = (index.by_string(query) if kind == "string"
                else index.by_shape(Shape(query), allow))
        report(hits, args.limit)
    elif args.command == "shape":
        hits = index.by_shape(Shape(args.pattern), allow)
        print("%d classes match\n" % len(hits))
        report(hits, args.limit)
    elif args.command == "strings":
        hits = index.by_string(args.text)
        print("%d classes reference %r\n" % (len(hits), args.text))
        report(hits, args.limit)
    elif args.command == "refs":
        hits = index.method_references(args.text, allow)
        print("%d methods reference %r\n" % (len(hits), args.text))
        for descriptor, method in hits[:args.limit]:
            print("  %-28s %s %s(%s)" % (index.simple(descriptor), method.returns, method.name,
                                          ",".join(method.params)))
        if len(hits) > args.limit:
            print("  … %d more" % (len(hits) - args.limit))
    elif args.command == "singletons":
        hits = index.singleton_fields(allow)
        print("%d singleton classes\n" % len(hits))
        for descriptor, name in hits[:args.limit]:
            superclass, interfaces = index.parents.get(descriptor, ("", []))
            print("  %-26s static %-4s extends %s implements %s" % (
                index.simple(descriptor), name,
                index.simple(superclass) if superclass else "?",
                ",".join(index.simple(i) for i in (interfaces or []))))
        if len(hits) > args.limit:
            print("  … %d more" % (len(hits) - args.limit))
    elif args.command == "statics":
        hits = index.static_fields(args.type, allow)
        print("%d static fields of %s\n" % (len(hits), args.type))
        for descriptor, name in hits[:args.limit]:
            print("  %-28s static %s" % (index.simple(descriptor), name))
        if len(hits) > args.limit:
            print("  … %d more" % (len(hits) - args.limit))
    elif args.command == "composer":
        tally = index.composer_tally(allow)
        print("likely Composer types\n")
        for descriptor, count in tally[:args.limit]:
            print("  %-44s %6d composables" % (index.simple(descriptor), count))
    elif args.command == "compose-card":
        shape = Shape("static void(*,*,*,*,*,*,*,*,%s,int)" % args.composer)
        hits = index.by_shape(shape, allow)
        print("%d classes match a 10-parameter card renderer\n" % len(hits))
        report(hits, args.limit)
    elif args.command == "class":
        found = False
        for descriptor, superclass, methods, fields in index.find_classes(args.name):
            found = True
            print("%s  extends %s" % (index.simple(descriptor), superclass))
            print("  fields: %s" % ", ".join(fields[:40]))
            for method in methods:
                print("  %s%s" % ("static " if method.is_static else "", method.signature()))
            print()
        if not found:
            print("no class matched %r" % args.name)
    else:
        parser.print_help()
    return 0


def selftest(index):
    """Assert the reader against structures this repository builds."""
    checks = []

    def check(condition, label):
        checks.append(label)
        if not condition:
            raise AssertionError(label)

    check(len(index.classes) > 500, "class table parsed (found %d)" % len(index.classes))
    check(any(c[0] == "Ldev/lyriscope/spotify/SpotifyMapping;" for c in index.classes),
          "SpotifyMapping present")
    check(any(c[0] == "Ldev/lyriscope/spotify/SpotifyMapping$Card;" for c in index.classes),
          "nested mapping group present")

    adapter = [c for c in index.classes if c[0] == "Ldev/lyriscope/spotify/ComposeAdapter;"]
    check(adapter, "ComposeAdapter present")
    names = {m.name for m in adapter[0][2]}
    check("createSongCard" in names, "method names read from class_data")
    card = [m for m in adapter[0][2] if m.name == "createSongCard"][0]
    check(len(card.params) == 2 and card.is_static, "createSongCard is static with 2 parameters")

    shape = Shape("static *(*,*)")
    check(shape.matches(card), "shape matcher accepts a wildcard signature")

    hits = index.by_shape(Shape("static ArrayList(ArrayList,*)"))
    check(any(c[0] == "Ldev/lyriscope/spotify/SpotifyLyricsOrder;" for c in hits),
          "static ArrayList(ArrayList,*) finds the stable-partition helper")

    strings = index.strings.get("Ldev/lyriscope/spotify/SpotifyInlineLyrics$InlineHost;", set())
    check("ivLyricsInlineHost" in strings, "string constants scanned from code_item")

    print("SELFTEST_PASSED checks=%d classes=%d" % (len(checks), len(index.classes)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
