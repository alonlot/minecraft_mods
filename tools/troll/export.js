// Packs the box UVs, writes layout.json, the Java export and the .bbmodel. Run after build_model.js:
//   eval(require('fs').readFileSync('<this file>', 'utf8'))
(function () {
  const fs = require('fs');
  const ROOT = 'C:\\Users\\alonl\\OneDrive\\Desktop\\harry potter mod\\';
  ModelProject.all.find(x => x.uuid === "96b4595f-f355-2c7c-d931-223234d57a27").select();
  const cubes = Project.elements.slice();
  const items = cubes.map(c => {
    const w = Math.ceil(c.to[0] - c.from[0] - 1e-6), h = Math.ceil(c.to[1] - c.from[1] - 1e-6), d = Math.ceil(c.to[2] - c.from[2] - 1e-6);
    return { c, w, h, d, fw: 2 * (d + w), fh: d + h };
  }).sort((a, b) => b.fh - a.fh || b.fw - a.fw);
  let x = 0, y = 0, rowH = 0;
  const layout = [];
  items.forEach(it => {
    if (x + it.fw > 256) { x = 0; y += rowH + 1; rowH = 0; }
    it.c.uv_offset = [x, y];
    it.c.box_uv = true;
    layout.push({ name: it.c.name, group: it.c.parent.name, from: it.c.from.slice(), to: it.c.to.slice(), u: x, v: y, w: it.w, h: it.h, d: it.d });
    x += it.fw + 1;
    rowH = Math.max(rowH, it.fh);
  });
  Project.elements.forEach(c => c.updateElement && c.updateElement());
  Canvas.updateAll();
  fs.writeFileSync(ROOT + 'tools\\troll\\layout.json', JSON.stringify(layout));
  fs.writeFileSync(ROOT + 'tools\\troll\\TrollModel_export.java', Codecs.modded_entity.compile());
  fs.writeFileSync(ROOT + 'blockbench_models\\mountain_troll_v3.bbmodel', Codecs.project.compile());
  return { bottom: y + rowH, cubes: cubes.length };
})();
