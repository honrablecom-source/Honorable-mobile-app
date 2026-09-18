const fs = require("fs"),
  vm = require("vm"),
  path = require("path");
const root = path.resolve(__dirname, "..");
const dir = path.join(
  root,
  "mobile-react-native/node_modules/lucide-react-native/dist/esm/icons"
);
const names = {
  Home: "house",
  PhotoLibrary: "images",
  Tune: "sliders-horizontal",
  CreditCard: "ticket",
  Timeline: "chart-no-axes-column",
  AccountCircle: "circle-user-round",
  Search: "search",
  ArrowUpward: "arrow-up",
  ArrowBack: "arrow-left",
  OpenInNew: "arrow-up-right",
  Close: "x",
  Shield: "shield",
  ChevronRight: "chevron-right",
};
function d(tag, a) {
  if (tag === "path") return a.d;
  if (tag === "circle")
    return `M ${+a.cx - a.r} ${a.cy} a ${a.r} ${a.r} 0 1 0 ${2 * a.r} 0 a ${
      a.r
    } ${a.r} 0 1 0 ${-2 * a.r} 0`;
  if (tag === "line") return `M ${a.x1} ${a.y1} L ${a.x2} ${a.y2}`;
  if (tag === "polyline" || tag === "polygon")
    return "M " + a.points + (tag === "polygon" ? " Z" : "");
  if (tag === "rect") {
    const x = +a.x,
      y = +a.y,
      w = +a.width,
      h = +a.height,
      r = +(a.rx || 0);
    return `M ${x + r} ${y} H ${x + w - r} Q ${x + w} ${y} ${x + w} ${
      y + r
    } V ${y + h - r} Q ${x + w} ${y + h} ${x + w - r} ${y + h} H ${
      x + r
    } Q ${x} ${y + h} ${x} ${y + h - r} V ${y + r} Q ${x} ${y} ${x + r} ${y} Z`;
  }
  throw Error(tag);
}
let code = `package app.honorable\n\n// Lucide paths from installed lucide-react-native, ISC license in assets/Lucide-LICENSE.txt.\nimport androidx.compose.ui.graphics.*\nimport androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.ui.graphics.vector.PathParser\nimport androidx.compose.ui.unit.dp\n\ninternal object HonorableIcons {\n private fun icon(name:String,vararg paths:String):ImageVector = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply { paths.forEach { data -> addPath(pathData=PathParser().parsePathString(data).toNodes(),stroke=SolidColor(Color.White),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) } }.build()\n`;
for (const [name, file] of Object.entries(names)) {
  const text = fs.readFileSync(path.join(dir, file + ".mjs"), "utf8");
  const nodes = vm.runInNewContext(
    "(" + text.slice(text.indexOf(", [") + 2, text.lastIndexOf("]);") + 1) + ")"
  );
  code += ` val ${name}:ImageVector by lazy { icon(${JSON.stringify(
    name
  )},${nodes.map(([tag, a]) => JSON.stringify(d(tag, a))).join(",")}) }\n`;
}
code += "}\n";
fs.writeFileSync(
  path.join(
    root,
    "android-app/app/src/main/java/app/honorable/HonorableIcons.kt"
  ),
  code
);
fs.copyFileSync(
  path.join(
    root,
    "mobile-react-native/node_modules/lucide-react-native/LICENSE"
  ),
  path.join(root, "android-app/app/src/main/assets/Lucide-LICENSE.txt")
);
const main = path.join(
  root,
  "android-app/app/src/main/java/app/honorable/MainActivity.kt"
);
let s = fs
  .readFileSync(main, "utf8")
  .replace(/Icons\.(?:AutoMirrored\.)?Rounded\.(\w+)/g, "HonorableIcons.$1")
  .replace(/^import androidx\.compose\.material\.icons.*\n/gm, "");
s = s.replace(/\n{3,}/g, "\n\n").trim() + "\n";
fs.writeFileSync(main, s);
