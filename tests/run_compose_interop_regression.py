#!/usr/bin/env python3
"""Resolve the Compose AndroidView interop the way a real host hands it over.

9.2 taught this the hard way: the card renderer declares its fourth parameter as the
Composer *interface* while the running instance is that interface's implementation, so
asking reflection for `composer.getClass()` finds nothing and the card silently fails to
mount. This runs the production ComposeAdapter against a synthetic host whose declared
Composer type and instance type differ, which is the shape every R8 build can take.

JDK only: no device, installed Spotify, account or network.
"""
import subprocess

from regression_runtime import ROOT, REPORTS, android_jar, compiled_classes, classpath, java_tool

work = REPORTS / "compose-interop"
work.mkdir(parents=True, exist_ok=True)
source = work / "ComposeInteropRegression.java"
source.write_text(r'''
package dev.lyriscope.spotify;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A host surface where the Composer is passed as its interface, not its implementation. */
public final class ComposeInteropRegression {

    public interface FakeFunction1 { Object invoke(Object value); }
    public interface FakeModifier { FakeModifier then(FakeModifier other); }
    public interface FakeComposer { }

    /** The only Compose runtime class in the host, as R8 leaves it: interface plus impl. */
    public static final class FakeComposerImpl implements FakeComposer {
        public FakeComposerImpl() { }
    }

    public abstract static class FakeModifierBase implements FakeModifier {
        @Override public FakeModifier then(FakeModifier other) { return other; }
    }

    /** Aspect-ratio modifier: the (float, boolean) constructor the adapter instantiates. */
    public static final class FakeAspect extends FakeModifierBase {
        public FakeAspect(float ratio, boolean matchHeight) { }
    }

    public static final class FakeEmpty {
        public static final FakeModifier a = new FakeModifierBase() { };
    }

    public static final class FakeFill {
        public static FakeModifier f(float fraction, FakeModifier base) { return base; }
    }

    public static final class FakePadding {
        public static FakeModifier y(float horizontal, float vertical, int direction, FakeModifier base) {
            return base;
        }
    }

    /** Compose's AndroidView slot: the Composer parameter is declared as the interface. */
    public static final class RenderOwner {
        public static void render(FakeFunction1 factory, FakeModifier modifier, FakeFunction1 update,
                                  FakeComposer composer, int changed, int defaults) { }
        public static void renderInline(FakeFunction1 factory, FakeModifier modifier, FakeFunction1 update,
                                        FakeComposer composer, int changed, int defaults) { }
    }

    private static int assertions;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static SpotifyMapping mapping(String renderer) throws Exception {
        SpotifyMapping.Card card = new SpotifyMapping.Card(
                RenderOwner.class.getName(), renderer, FakeComposer.class.getName());
        SpotifyMapping.Compose compose = new SpotifyMapping.Compose(
                FakeFunction1.class.getName(), FakeModifier.class.getName(),
                RenderOwner.class.getName(), renderer, FakeAspect.class.getName());
        SpotifyMapping.Inline inline = new SpotifyMapping.Inline(
                null, null, null, null, null, FakeComposer.class.getName(), null, null,
                renderer, null, FakeEmpty.class.getName(), FakeFill.class.getName(),
                FakePadding.class.getName());
        Constructor<SpotifyMapping> constructor = SpotifyMapping.class.getDeclaredConstructor(
                String.class, String.class, SpotifyMapping.Card.class, SpotifyMapping.Compose.class,
                SpotifyMapping.Availability.class, SpotifyMapping.Presence.class,
                SpotifyMapping.Metadata.class, SpotifyMapping.Inline.class, SpotifyMapping.Order.class,
                SpotifyMapping.Video.class, SpotifyMapping.Karaoke.class);
        constructor.setAccessible(true);
        return constructor.newInstance("test", null, card, compose, null, null, null, inline,
                null, null, null);
    }

    /** The instance is the interface's implementation, exactly as SpotifyComponents hands it over. */
    public static void main(String[] args) throws Exception {
        Object composer = new FakeComposerImpl();

        Class<?> interop = Class.forName("dev.lyriscope.spotify.ComposeAdapter$Interop");
        Constructor<?> cardInterop = interop.getDeclaredConstructor(SpotifyMapping.class, Object.class);
        cardInterop.setAccessible(true);
        Object mounted = cardInterop.newInstance(mapping("render"), composer);

        Field render = interop.getDeclaredField("render");
        render.setAccessible(true);
        Method resolved = (Method) render.get(mounted);
        check(resolved.getName().equals("render"), "card interop resolved the wrong method: " + resolved);
        check(resolved.getParameterTypes()[3] == FakeComposer.class,
                "card interop must resolve through the table's Composer type");
        check(((Method) render.get(mounted)).getReturnType() == void.class, "card slot must be a Unit slot");

        Class<?> inlineInterop = Class.forName("dev.lyriscope.spotify.ComposeAdapter$InlineInterop");
        Constructor<?> inline = inlineInterop.getDeclaredConstructor(SpotifyMapping.class, Object.class);
        inline.setAccessible(true);
        Object inlineMounted = inline.newInstance(mapping("renderInline"), composer);
        Field inlineRender = inlineInterop.getDeclaredField("render");
        inlineRender.setAccessible(true);
        Method inlineResolved = (Method) inlineRender.get(inlineMounted);
        check(inlineResolved.getName().equals("renderInline"),
                "inline interop resolved the wrong method: " + inlineResolved);
        check(inlineResolved.getParameterTypes()[3] == FakeComposer.class,
                "inline interop must resolve through the table's Composer type");

        System.out.println("compose interop: " + assertions + " assertions passed.");
    }
}
''')

dependencies = classpath(work, compiled_classes("spotify-module"), compiled_classes("shared"), android_jar())
# Work comes first on the classpath, so this Log replaces android.jar's "Stub!" body.
subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", dependencies, "-d", str(work),
                str(ROOT / "tests/android/util/Log.java"), str(source)], check=True)
result = subprocess.run([java_tool("java"), "-cp", dependencies,
                         "dev.lyriscope.spotify.ComposeInteropRegression"],
                        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
(work / "result.txt").write_text(result.stdout)
print(result.stdout, end="")
raise SystemExit(result.returncode)
