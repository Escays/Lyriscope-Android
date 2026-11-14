"""Audit SpotifyMapping.java against a real Spotify APK, without a device.

Parses the mapping table out of the Java source and re-resolves every anchor in the
APK's DEX, exactly like SpotifyMappingCheck does at install time. Use it after adding
a build, or after a Spotify update, to see which anchors moved.

    python tests/run_mapping_audit.py --apk path/to/spotify.apk
    python tests/run_mapping_audit.py --apk path/to/spotify.apk --version 9.2.0.2053
"""
import argparse
import re
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "spotify-module" / "tools"))
from mapping_probe import Dex, Index, descriptor, uleb  # noqa: E402

TABLE = ROOT / "spotify-module" / "src" / "main" / "java" / "dev" / "lyriscope" / "spotify" / "SpotifyMapping.java"
GROUPS = ("Card", "Compose", "Availability", "Presence", "Metadata", "Inline", "Order", "Video", "Karaoke")
CONTEXT_TRACK = "Lcom/spotify/player/model/ContextTrack;"
MAYBE = "io.reactivex.rxjava3.core.Maybe"


def split_args(text):
    parts, current, quote = [], "", None
    for char in text:
        if quote:
            current += char
            if char == quote:
                quote = None
        elif char in "\"'":
            quote = char
            current += char
        elif char == ",":
            parts.append(current.strip())
            current = ""
        else:
            current += char
    if current.strip():
        parts.append(current.strip())
    out = []
    for part in parts:
        if part == "null":
            out.append(None)
        elif part.startswith('"'):
            out.append(part[1:-1])
        elif re.fullmatch(r"-?\d+", part):
            out.append(int(part))
        else:
            out.append(part)
    return out


def parse_table():
    text = TABLE.read_text(encoding="utf-8")
    builds = {}
    for block in re.finditer(r"new SpotifyMapping\((.*?)\)\s*;", text, re.S):
        body = block.group(1)
        head = re.match(r'\s*"([^"]+)"\s*,\s*(null|"[^"]+")\s*,', body)
        if not head:
            continue
        version = head.group(1)
        unit = None if head.group(2) == "null" else head.group(2).strip('"')
        groups = {}
        for name in GROUPS:
            found = re.search(r"new %s\((.*?)\)" % name, body, re.S)
            if found:
                groups[name] = split_args(found.group(1))
        builds[version] = (unit, groups)
    return builds


class Fields:
    """Field names and descriptors per class, read once from the DEX files."""

    def __init__(self, apk):
        self.table = {}
        with zipfile.ZipFile(apk) as archive:
            names = sorted((n for n in archive.namelist()
                            if n.startswith("classes") and n.endswith(".dex")), key=lambda n: (len(n), n))
            for name in names:
                dex = Dex(archive.read(name))
                for index in range(dex.class_defs_size):
                    base = dex.class_defs_off + index * 32
                    class_idx, _flags, _super, _ifaces, _source, _ann, data_off, _values = \
                        struct.unpack_from("<8I", dex.data, base)
                    if not data_off:
                        continue
                    self.table[dex.type(class_idx)] = self.read(dex, data_off)

    @staticmethod
    def read(dex, offset):
        counts, cursor = [], offset
        for _ in range(4):
            value, cursor = uleb(dex.data, cursor)
            counts.append(value)
        out = {}
        for total, static in ((counts[0], True), (counts[1], False)):
            idx = 0
            for _ in range(total):
                diff, cursor = uleb(dex.data, cursor)
                _flags, cursor = uleb(dex.data, cursor)
                idx += diff
                _owner, name, kind = dex.field_id(idx)
                out[name] = (kind, static)
        return out


class Checker:
    def __init__(self, index, fields):
        self.index = index
        self.fields = fields
        self.by_name = {c[0]: c for c in index.classes}
        self.failures = []
        self.passes = 0

    # -- helpers ---------------------------------------------------------
    def cls(self, name):
        return self.by_name.get(descriptor(name)) if name else None

    def sigs(self, name):
        row = self.cls(name)
        return [] if row is None else row[2]

    def field(self, owner, name):
        return self.fields.table.get(descriptor(owner), {}).get(name)

    def assignable(self, child, parent):
        want = descriptor(parent)
        todo = [descriptor(child)]
        seen = set()
        while todo:
            current = todo.pop()
            if current in seen:
                continue
            seen.add(current)
            if current == want:
                return True
            superclass, interfaces = self.index.parents.get(current, (None, []))
            if superclass:
                todo.append(superclass)
            todo.extend(interfaces)
        return False

    def ok(self, label, condition, detail=""):
        if condition:
            self.passes += 1
        else:
            self.failures.append(label + (" (" + detail + ")" if detail else ""))

    def method(self, owner, name, params=None, returns=None, static=None):
        for m in self.sigs(owner):
            if m.name != name:
                continue
            if params is not None and len(m.params) != len(params):
                continue
            if params is not None and any(p != "*" and p != actual
                                          for p, actual in zip(params, m.params)):
                continue
            if returns is not None and m.returns != returns:
                continue
            if static is not None and m.is_static != static:
                continue
            return m
        return None

    # -- groups ----------------------------------------------------------
    def card(self, args):
        owner, renderer, composer = args
        found = self.method(owner, renderer, returns="V", static=True)
        if found is None:
            self.ok("card.renderer", False, renderer + " missing")
            return
        types = found.params
        self.ok("card.renderer", len(types) == 10 and types[8] == descriptor(composer)
                and types[9] == "I", "signature drift: " + ",".join(types))

    def compose(self, args, composer):
        function, modifier, owner, renderer, aspect = args
        row = self.cls(function)
        self.ok("compose.function", row is not None and any(m.code_off == 0 for m in row[2])
                and all(m.name != "<init>" for m in row[2]), "not a lambda interface")
        self.ok("compose.modifier", self.cls(modifier) is not None)
        found = self.method(owner, renderer, params=[descriptor(function), descriptor(modifier),
                                                    descriptor(function), descriptor(composer),
                                                    "I", "I"], returns="V", static=True)
        self.ok("compose.interop", found is not None, owner + "." + renderer)
        self.ok("compose.aspect", any(m.is_ctor and m.params == ["F", "Z"]
                                      for m in self.sigs(aspect)))

    def availability(self, args):
        owner, method, branch, state, seed, model, card = args
        found = self.method(owner, method,
                            params=[CONTEXT_TRACK, "Ljava/lang/String;"] if branch is None
                            else [CONTEXT_TRACK])
        self.ok("availability.provider", found is not None
                and found.returns == "L" + MAYBE.replace(".", "/") + ";", owner)
        if branch is not None:
            self.ok("availability.branch", self.field(owner, branch) is not None)
        seed_type = self.field(state, seed)
        self.ok("availability.state", seed_type is not None and seed_type[1], state + "." + seed)
        lyrics = self.field(state, "a")
        ctor = [m for m in self.sigs(model) if m.is_ctor and len(m.params) == 6
                and m.params[1:] == ["Ljava/lang/String;"] * 5]
        self.ok("availability.model", bool(ctor) and lyrics is not None
                and ctor[0].params[0] == lyrics[0],
                "model ctor takes %s, state.a is %s" % (ctor[0].params[0] if ctor else "?",
                                                       lyrics[0] if lyrics else "missing"))
        self.ok("availability.card", any(m.is_ctor and m.params == [descriptor(model)]
                                         for m in self.sigs(card)))
    def presence(self, args):
        section, lyrics, event, arity, extra = args
        self.ok("presence.sections", self.assignable(lyrics, section),
                lyrics + " is not a " + section)
        track = CONTEXT_TRACK
        expected = ([track, "Ljava/lang/String;", "Ljava/util/List;"] if arity == 3
                    else [track, "Ljava/lang/String;", "Ljava/util/List;", "Ljava/util/List;",
                          descriptor(extra)])
        self.ok("presence.event", any(m.is_ctor and m.params == expected for m in self.sigs(event)),
                event)

    def metadata(self, args):
        client, request, track, method = args
        self.ok("metadata.client", self.method(client, method,
                                               params=[descriptor(request)]) is not None, client + "." + method)
        self.ok("metadata.request", any(m.is_ctor and m.params == ["Ljava/lang/String;", "Z",
                                                                  "Lkotlin/jvm/functions/Function1;"]
                                        for m in self.sigs(request)), request)
        self.ok("metadata.track", self.cls(track) is not None)

    def inline(self, args, composer_map):
        (renderer, branch, visibility, availability, model, composer, menu, gate, renderer_method,
         gate_method, empty, fill, padding) = args
        self.ok("inline.renderer", self.method(renderer, renderer_method,
                                              params=["Ljava/lang/Object;"] * 5) is not None,
                renderer + "." + renderer_method)
        if branch is not None:
            found = self.field(renderer, branch)
            self.ok("inline.branch", found is not None and found[0] == "I", renderer + "." + branch)
        # Gates and the native toggle are optional: a build may map the slot before re-anchoring them.
        if visibility is not None:
            self.ok("inline.visibility", self.method(visibility, "invokeSuspend",
                                                     params=["Ljava/lang/Object;"]) is not None)
        if availability is not None:
            self.ok("inline.availability", self.method(availability, "invokeSuspend",
                                                       params=["Ljava/lang/Object;"]) is not None)
        self.ok("inline.model", self.cls(model) is not None and self.cls(composer) is not None)
        if menu is not None:
            self.ok("inline.menu", self.method(menu, "apply", params=["Ljava/lang/Object;"]) is not None
                    and self.method(gate, gate_method, params=[CONTEXT_TRACK]) is not None)
        modifiers_ok = self.field(empty, "a") is not None
        if fill is not None:
            modifiers_ok = modifiers_ok and self.method(
                fill, "f", params=["F", descriptor(composer_map)]) is not None
        if padding is not None:
            modifiers_ok = modifiers_ok and self.method(
                padding, "y", params=["F", "F", "I", descriptor(composer_map)]) is not None
        self.ok("inline.modifier", modifiers_ok)

    def order(self, args):
        owner, state, section, lyrics, filter_name = args
        found = self.method(owner, filter_name, params=[descriptor(state), "Ljava/util/Set;"],
                            returns="Ljava/util/ArrayList;", static=True)
        self.ok("order.filter", found is not None, owner + "." + filter_name)
        row = self.cls(state)
        self.ok("order.state", row is not None and any(m.code_off == 0 for m in row[2]),
                "state is not an interface")
        self.ok("order.shape", self.assignable(lyrics, section))

    def video(self, args):
        renderer, button, update_context, update, view = args
        self.ok("video.renderer", self.method(renderer, update,
                                              params=["Ljava/lang/Object;", descriptor(update_context)])
                is not None and self.method(renderer, view) is not None)
        self.ok("video.button", self.cls(button) is not None)

    def karaoke(self, args):
        (settings, optional, response, lyrics_shape, support_shape, mapper, repository,
         pool, state_flow, *services) = args
        offline = self.field(settings, "a")
        self.ok("karaoke.settings", offline is not None and offline[0] == "Z")
        self.ok("karaoke.optional", self.method(optional, "c") is not None
                and self.method(optional, "b") is not None)
        self.ok("karaoke.shapes", self.cls(response) is not None and self.cls(lyrics_shape) is not None
                and self.cls(support_shape) is not None)
        self.ok("karaoke.repository", self.field(mapper, "c") is not None
                and self.field(repository, "d") is not None)
        self.ok("karaoke.player", self.method(pool, "c") is not None
                and self.method(state_flow, "e") is not None)
        self.ok("karaoke.services", bool(services) and all(self.cls(s) for s in services))


def audit(apk, only=None):
    builds = parse_table()
    index = Index(apk, scan_strings=False)
    fields = Fields(apk)
    total = 0
    for version, (unit, groups) in builds.items():
        if only and version != only:
            continue
        checker = Checker(index, fields)
        if "Card" in groups:
            checker.card(groups["Card"])
        if "Compose" in groups:
            checker.compose(groups["Compose"], groups["Card"][2])
        if "Availability" in groups:
            checker.availability(groups["Availability"])
        if "Presence" in groups:
            checker.presence(groups["Presence"])
        if unit:
            checker.ok("kotlin.unit", checker.field(unit, "a") is not None)
        if "Metadata" in groups:
            checker.metadata(groups["Metadata"])
        if "Inline" in groups:
            checker.inline(groups["Inline"], groups["Compose"][1])
        if "Order" in groups:
            checker.order(groups["Order"])
        if "Video" in groups:
            checker.video(groups["Video"])
        if "Karaoke" in groups:
            checker.karaoke(groups["Karaoke"])
        total += checker.passes
        status = "OK" if not checker.failures else "FAIL"
        print("%-12s %-4s anchors=%d failed=%d" % (version, status, checker.passes,
                                                   len(checker.failures)))
        for label in checker.failures:
            print("    - " + label)
    if only and only not in builds:
        print("no mapping for %s in SpotifyMapping.java" % only)
        return 1
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--version", help="audit a single mapped build")
    args = parser.parse_args(argv)
    return audit(args.apk, args.version)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
