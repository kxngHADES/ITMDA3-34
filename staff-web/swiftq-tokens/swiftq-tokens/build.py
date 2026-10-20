"""Generate platform token files from tokens.json.

    python build.py

Outputs into ./dist:
  web/tokens.css         CSS custom properties + text classes (Next.js staff & admin web)
  web/tokens.ts          Typed JS object (Next.js, charts, inline styles)
  android/SwiftQTokens.kt  Jetpack Compose colours, type, shapes, spacing (patient + staff mobile)
  android/colors.xml     Android resource colours (splash, launcher icon, XML views)
"""
import json, re, pathlib

ROOT = pathlib.Path(__file__).parent
SRC = json.loads((ROOT / "tokens.json").read_text())
OUT = ROOT / "dist"


# ---------- helpers ----------
def leaves(node, path=()):
    """Yield (path, token) for every token (dict with $value)."""
    for k, v in node.items():
        if k.startswith("$") or not isinstance(v, dict):
            continue
        if "$value" in v:
            yield path + (k,), v
        else:
            yield from leaves(v, path + (k,))


def resolve(value):
    if isinstance(value, str):
        m = re.fullmatch(r"\{(.+)\}", value)
        if m:
            node = SRC
            for part in m.group(1).split("."):
                node = node[part]
            return resolve(node["$value"])
    return value


def alias_path(value):
    m = re.fullmatch(r"\{(.+)\}", value) if isinstance(value, str) else None
    return m.group(1).split(".") if m else None


kebab = lambda parts: "-".join(parts)
camel = lambda parts: parts[0] + "".join(p[:1].upper() + p[1:] for p in "-".join(parts[1:]).split("-") if p) if len(parts) > 1 else parts[0]
pascal = lambda parts: "".join(p[:1].upper() + p[1:] for p in "-".join(parts).split("-") if p)
px = lambda s: float(str(s).replace("px", ""))
num = lambda f: str(int(f)) if float(f).is_integer() else str(f)

colors = list(leaves(SRC["color"]))
semantic = list(leaves(SRC["semantic"]))
typo = list(leaves(SRC["typography"]))
radius = list(leaves(SRC["radius"]))
space = list(leaves(SRC["space"]))
size = list(leaves(SRC["size"]))
elev = list(leaves(SRC["elevation"]))
motion = list(leaves(SRC["motion"]))
family = SRC["font"]["family"]["$value"]

HEADER = "Generated from tokens.json by build.py. Do not edit by hand."

# ---------- web/tokens.css ----------
css = [f"/* {HEADER} */", ":root {"]
css.append("  /* Colour palette */")
for p, t in colors:
    css.append(f"  --sq-color-{kebab(p)}: {t['$value'].lower()};")
css.append("\n  /* Semantic colours: use these in components */")
for p, t in semantic:
    ap = alias_path(t["$value"])
    val = f"var(--sq-{kebab(ap)})" if ap else t["$value"]
    css.append(f"  --sq-{kebab(p)}: {val};")
css.append("\n  /* Type */")
css.append(f"  --sq-font-family: {', '.join(repr(f).replace(chr(39), chr(34)) if ' ' in f else f for f in family)};")
for p, t in typo:
    v = t["$value"]; n = kebab(p)
    css.append(f"  --sq-text-{n}-size: {num(v['fontSize'])}px;")
    css.append(f"  --sq-text-{n}-line: {num(v['lineHeight'])}px;")
    css.append(f"  --sq-text-{n}-weight: {v['fontWeight']};")
    if v["letterSpacing"]:
        css.append(f"  --sq-text-{n}-tracking: {num(v['letterSpacing'] / 100)}em;")
css.append("\n  /* Radius */")
for p, t in radius:
    css.append(f"  --sq-radius-{kebab(p)}: {t['$value']};")
css.append("\n  /* Spacing */")
for p, t in space:
    css.append(f"  --sq-space-{kebab(p)}: {t['$value']};")
css.append("\n  /* Sizes */")
for p, t in size:
    css.append(f"  --sq-size-{kebab(p)}: {t['$value']};")
css.append("\n  /* Elevation */")
for p, t in elev:
    v = t["$value"]
    css.append(f"  --sq-shadow-{kebab(p)}: {v['offsetX']} {v['offsetY']} {v['blur']} {v['spread']} {v['color'].lower()};")
css.append("\n  /* Motion */")
for p, t in motion:
    css.append(f"  --sq-duration-{kebab(p)}: {t['$value']};")
css.append("}\n")
css.append("/* Text styles: <h1 class=\"sq-text-title-1\"> */")
for p, t in typo:
    n = kebab(p); v = t["$value"]
    rule = (f".sq-text-{n} {{ font-family: var(--sq-font-family); font-size: var(--sq-text-{n}-size); "
            f"line-height: var(--sq-text-{n}-line); font-weight: var(--sq-text-{n}-weight);")
    if v["letterSpacing"]:
        rule += f" letter-spacing: var(--sq-text-{n}-tracking);"
    css.append(rule + " }")

# ---------- web/tokens.ts ----------
def nest(items, transform):
    tree = {}
    for p, t in items:
        d = tree
        for k in p[:-1]:
            d = d.setdefault(k, {})
        d[p[-1]] = transform(t)
    return tree

ts_obj = {
    "color": nest(colors, lambda t: t["$value"].upper()),
    "semantic": nest(semantic, lambda t: resolve(t["$value"]).upper()),
    "font": {"family": ", ".join(family)},
    "typography": nest(typo, lambda t: t["$value"]),
    "radius": nest(radius, lambda t: px(t["$value"])),
    "space": nest(space, lambda t: px(t["$value"])),
    "size": nest(size, lambda t: px(t["$value"])),
    "shadow": nest(elev, lambda t: "{offsetX} {offsetY} {blur} {spread} {color}".format(**t["$value"])),
    "duration": nest(motion, lambda t: int(t["$value"].replace("ms", ""))),
}
ts = (f"// {HEADER}\n// Numbers are px (radius, space, size, typography) and ms (duration).\n\n"
      f"export const tokens = {json.dumps(ts_obj, indent=2)} as const;\n\n"
      "export type TicketStatus = keyof typeof tokens.semantic.ticket;\n"
      "export default tokens;\n")

# ---------- android/SwiftQTokens.kt ----------
def kcolor(hexv):
    h = hexv.lstrip("#").upper()
    if len(h) == 8:  # RRGGBBAA -> AARRGGBB
        h = h[6:] + h[:6]
    else:
        h = "FF" + h
    return f"Color(0x{h})"

kt = [f"// {HEADER}", "package com.swiftq.designsystem", "",
      "import androidx.compose.foundation.shape.RoundedCornerShape",
      "import androidx.compose.ui.graphics.Color",
      "import androidx.compose.ui.text.TextStyle",
      "import androidx.compose.ui.text.font.FontFamily",
      "import androidx.compose.ui.text.font.FontWeight",
      "import androidx.compose.ui.unit.dp",
      "import androidx.compose.ui.unit.em",
      "import androidx.compose.ui.unit.sp", "",
      "/** Raw palette. Prefer [SwiftQSemantic] in UI code. */",
      "object SwiftQColors {"]
for p, t in colors:
    kt.append(f"    val {pascal(p)} = {kcolor(t['$value'])}  // {t.get('$description', '')}")
kt += ["}", "", "/** What each colour is for. Use these in composables. */", "object SwiftQSemantic {"]
groups = {}
for p, t in semantic:
    groups.setdefault(p[0], []).append((p[1:], t))
for g, items in groups.items():
    kt.append(f"    object {pascal([g])} {{")
    for p, t in items:
        ap = alias_path(t["$value"])
        ref = f"SwiftQColors.{pascal(ap[1:])}" if ap else kcolor(t["$value"])
        kt.append(f"        val {pascal(p)} = {ref}")
    kt.append("    }")
kt += ["}", "",
       "/** Spacing, radius and sizes in dp. */",
       "object SwiftQSpace {"]
for p, t in space:
    kt.append(f"    val S{p[0]} = {num(px(t['$value']))}.dp")
kt += ["}", "", "object SwiftQRadius {"]
for p, t in radius:
    name = {"2xl": "Xxl", "full": "Full"}.get(p[0], pascal(p))
    kt.append(f"    val {name} = {num(px(t['$value']))}.dp")
kt += ["}", "", "object SwiftQShapes {"]
for p, t in radius:
    name = {"2xl": "Xxl", "full": "Full"}.get(p[0], pascal(p))
    kt.append(f"    val {name} = RoundedCornerShape(SwiftQRadius.{name})" if p[0] != "full" else "    val Full = RoundedCornerShape(percent = 50)")
kt += ["}", "", "object SwiftQSize {"]
for p, t in size:
    kt.append(f"    val {pascal(p)} = {num(px(t['$value']))}.dp")
kt += ["}", "", "object SwiftQMotion {"]
for p, t in motion:
    kt.append(f"    const val {pascal(p)}Ms = {t['$value'].replace('ms', '')}")
kt += ["}", "",
       "/**",
       " * Type scale. Pass your Inter FontFamily once fonts are in res/font, e.g.",
       " *   val inter = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium),",
       " *                          Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_bold, FontWeight.Bold))",
       " *   val type = SwiftQType(inter)",
       " */",
       "class SwiftQType(private val family: FontFamily = FontFamily.Default) {"]
wmap = {400: "Normal", 500: "Medium", 600: "SemiBold", 700: "Bold", 800: "ExtraBold"}
for p, t in typo:
    v = t["$value"]
    ls = f", letterSpacing = {num(v['letterSpacing'] / 100)}.em" if v["letterSpacing"] else ""
    kt.append(f"    val {camel(list(p)) if False else pascal(p)[0].lower() + pascal(p)[1:]} = TextStyle(fontFamily = family, fontWeight = FontWeight.{wmap[v['fontWeight']]}, "
              f"fontSize = {num(v['fontSize'])}.sp, lineHeight = {num(v['lineHeight'])}.sp{ls})")
kt.append("}")

# ---------- android/colors.xml ----------
def xcolor(hexv):
    h = hexv.lstrip("#").upper()
    return "#" + (h[6:] + h[:6] if len(h) == 8 else h)

xml = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {HEADER} -->", "<resources>"]
for p, t in colors:
    xml.append(f'    <color name="sq_{"_".join(p).replace("-", "_")}">{xcolor(t["$value"])}</color>')
xml.append("    <!-- Semantic -->")
for p, t in semantic:
    ap = alias_path(t["$value"])
    val = f'@color/sq_{"_".join(ap[1:]).replace("-", "_")}' if ap else xcolor(t["$value"])
    xml.append(f'    <color name="sq_{"_".join(p).replace("-", "_")}">{val}</color>')
xml.append("</resources>")

# ---------- write ----------
(OUT / "web").mkdir(parents=True, exist_ok=True)
(OUT / "android").mkdir(parents=True, exist_ok=True)
(OUT / "web" / "tokens.css").write_text("\n".join(css) + "\n")
(OUT / "web" / "tokens.ts").write_text(ts)
(OUT / "android" / "SwiftQTokens.kt").write_text("\n".join(kt) + "\n")
(OUT / "android" / "colors.xml").write_text("\n".join(xml) + "\n")
print("Built:", *[str(f.relative_to(ROOT)) for f in sorted(OUT.rglob("*.*"))], sep="\n  ")
