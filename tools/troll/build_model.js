// Rebuilds the mountain troll in the open Blockbench project. Run through the MCP's risky_eval:
//   eval(require('fs').readFileSync('<this file>', 'utf8'))
// Blockbench space: y=0 at the feet, +Y up, front is -Z, and +X is the troll's OWN RIGHT (the codec
// negates X on export, so +X lands on Java -X, which is the entity's right in vanilla's convention).
// Every cube has integer dimensions so its box UV maps one texel per pixel.
(function () {
  const PROJECT = "96b4595f-f355-2c7c-d931-223234d57a27";
  const ROOT = "C:\\Users\\alonl\\OneDrive\\Desktop\\harry potter mod\\tools\\troll\\";
  const p = ModelProject.all.find(x => x.uuid === PROJECT);
  p.select();
  Project.elements.slice().forEach(e => e.remove());
  Project.groups.slice().forEach(g => { if (Project.groups.includes(g)) g.remove(false); });
  const tex = Texture.all[0];
  Project.texture_width = 256;
  Project.texture_height = 256;

  const G = {};
  function g(name, origin, rotation, parent) {
    const o = new Group({ name, origin, rotation: rotation || [0, 0, 0] });
    o.addTo(parent ? G[parent] : undefined);
    o.init();
    G[name] = o;
    return o;
  }
  function c(name, group, from, to) {
    const o = new Cube({ name, from, to, box_uv: true, uv_offset: [0, 0] });
    o.addTo(G[group]);
    o.init();
    o.applyTexture(tex, true);
    return o;
  }

  // hull carries the whole troll; root stays put, so the dropped club is not dragged over when it falls.
  g("root", [0, 0, 0]);
  g("hull", [0, 0, 0], [0, 0, 0], "root");

  // Torso: a pear. Nothing here is above the head's chin in front of it; the head sits forward of the
  // yoke, so the face always reads against the chest.
  g("body", [0, 18, 0], [-6, 0, 0], "hull");
  c("pelvis", "body", [-11, 13, -9], [11, 20, 8]);
  c("waist", "body", [-12, 19, -6], [12, 31, 9]);
  c("chest", "body", [-11, 30, -9], [11, 41, 9]);
  c("yoke", "body", [-15, 39, -7], [15, 46, 8]);
  c("traps", "body", [-9, 45, -5], [9, 49, 7]);
  c("hump", "body", [-7, 47, 0], [7, 51, 8]);
  c("neck", "body", [-6, 41, -11], [6, 49, -2]);
  c("pec_l", "body", [-10, 33, -11], [-1, 40, -8]);
  c("pec_r", "body", [1, 33, -11], [10, 40, -8]);
  c("belt", "body", [-12, 15, -10], [12, 17, 9]);

  g("belly", [0, 24, -8], [0, 0, 0], "body");
  c("belly_top", "belly", [-12, 27, -13], [12, 33, -5]);
  c("belly_mid", "belly", [-14, 20, -16], [14, 28, -5]);
  c("belly_low", "belly", [-12, 15, -15], [12, 21, -5]);
  c("belly_sag", "belly", [-8, 12, -13], [8, 16, -5]);
  c("belly_front", "belly", [-9, 18, -18], [9, 27, -16]);

  g("loin_front", [0, 15, -7.5], [0, 0, 0], "body");
  c("loin_f", "loin_front", [-6, 4, -8], [6, 15, -7]);
  g("loin_back", [0, 16, 9.5], [0, 0, 0], "body");
  c("loin_b", "loin_back", [-8, 5, 9], [8, 16, 10]);

  // A small blunt head on a low thrust, with an underbite.
  g("head", [0, 47, -8], [6, 0, 0], "body");
  c("cranium", "head", [-6, 47, -19], [6, 56, -8]);
  c("crown", "head", [-4, 56, -17], [4, 58, -10]);
  c("skull_back", "head", [-5, 48, -8], [5, 55, -6]);
  c("brow", "head", [-7, 52, -21], [7, 54, -18]);
  c("nose", "head", [-2.5, 48, -22], [2.5, 52, -19]);
  c("cheek_l", "head", [-7, 47, -17], [-5, 50, -12]);
  c("cheek_r", "head", [5, 47, -17], [7, 50, -12]);
  c("muzzle", "head", [-5, 46, -21], [5, 48, -17]);
  g("jaw", [0, 47, -12], [0, 0, 0], "head");
  c("jaw_main", "jaw", [-6, 42, -22], [6, 47, -11]);
  c("chin", "jaw", [-3.5, 41, -22], [3.5, 42, -17]);
  c("tusk_l", "jaw", [-5, 46, -22.5], [-3, 50, -20.5]);
  c("tusk_r", "jaw", [3, 46, -22.5], [5, 50, -20.5]);
  c("tooth_l", "jaw", [-1.5, 46, -22.2], [-0.5, 48, -21.2]);
  c("tooth_r", "jaw", [0.5, 46, -22.2], [1.5, 48, -21.2]);
  g("ear_r", [6, 51, -12], [0, 24, -14], "head");
  c("ear_r_flap", "ear_r", [6, 49, -12.5], [11, 53, -11.5]);
  c("ear_r_tip", "ear_r", [11, 48, -12.5], [13, 51, -11.5]);
  g("ear_l", [-6, 51, -12], [0, -24, 14], "head");
  c("ear_l_flap", "ear_l", [-11, 49, -12.5], [-6, 53, -11.5]);
  c("ear_l_tip", "ear_l", [-13, 48, -12.5], [-11, 51, -11.5]);

  // Right arm: the club arm. The handle runs through three finger rings built in the club's own frame,
  // so the fist reads as wrapped round the shaft from every side. fingers_r and club share one pivot
  // and one rotation; fingers_r stays when the club is hidden.
  g("arm_r", [17, 42, 0], [-2, 0, 5], "body");
  c("deltoid_r", "arm_r", [12, 39, -6], [22, 46, 5]);
  c("upper_r", "arm_r", [13, 29, -5], [21, 40, 4]);
  g("forearm_r", [17, 30, 0], [24, 0, 0], "arm_r");
  c("fore_r", "forearm_r", [12, 20, -5], [22, 31, 5]);
  c("wrist_r", "forearm_r", [13, 17, -4], [21, 21, 4]);
  g("hand_r", [17, 19, 0], [-14, 0, 0], "forearm_r");
  c("palm_r", "hand_r", [13, 12, -3], [21, 19, 5]);
  g("fingers_r", [17, 15.5, -1], [56, 0, 16], "hand_r");
  c("finger_r1", "fingers_r", [13.5, 17, -4.5], [20.5, 20, 3.5]);
  c("finger_r2", "fingers_r", [13.5, 14, -4.5], [20.5, 17, 3.5]);
  c("finger_r3", "fingers_r", [13.5, 11, -4.5], [20.5, 14, 3.5]);
  c("thumb_r", "fingers_r", [12, 14, -2.5], [14, 19, 2.5]);
  g("club", [17, 15.5, -1], [56, 0, 16], "hand_r");
  c("club_butt", "club", [14.5, 21, -3.5], [19.5, 22, 1.5]);
  c("club_handle", "club", [15, 9, -3], [19, 21, 1]);
  c("club_shaft", "club", [14.5, 1, -3.5], [19.5, 9, 1.5]);
  c("club_head", "club", [13, -7, -5], [21, 1, 3]);
  c("club_tip", "club", [14, -9, -4], [20, -7, 2]);
  c("club_knot_a", "club", [11, -5, -3], [13, -1, 1]);
  c("club_knot_b", "club", [21, -6, -3], [23, -2, 1]);
  c("club_knot_c", "club", [15, -4, -7], [19, -1, -5]);
  c("club_knot_d", "club", [15, 3, 1.5], [19, 6, 3.5]);

  // Left arm: open, hanging, a long clawed hand.
  g("arm_l", [-17, 42, 0], [4, 0, -5], "body");
  c("deltoid_l", "arm_l", [-22, 39, -6], [-12, 46, 5]);
  c("upper_l", "arm_l", [-21, 29, -5], [-13, 40, 4]);
  g("forearm_l", [-17, 30, 0], [10, 0, 0], "arm_l");
  c("fore_l", "forearm_l", [-22, 20, -5], [-12, 31, 5]);
  c("wrist_l", "forearm_l", [-21, 17, -4], [-13, 21, 4]);
  g("hand_l", [-17, 19, 0], [0, 0, 0], "forearm_l");
  c("palm_l", "hand_l", [-21, 12, -4], [-13, 19, 5]);
  c("claw_l1", "hand_l", [-21, 7, -4], [-19, 12, -1]);
  c("claw_l2", "hand_l", [-18, 6, -4], [-16, 12, -1]);
  c("claw_l3", "hand_l", [-15, 7, -4], [-13, 12, -1]);
  c("thumb_l", "hand_l", [-13, 10, 0], [-11, 16, 3]);

  // Short bowed legs and wide flat feet.
  g("leg_r", [8, 18, 0], [0, 0, 0], "hull");
  c("thigh_r", "leg_r", [3, 9, -6], [14, 20, 6]);
  g("shin_r", [8.5, 10, 0], [0, 0, 0], "leg_r");
  c("calf_r", "shin_r", [4, 3, -5], [13, 11, 5]);
  g("foot_r", [8.5, 4, 0], [0, 0, 0], "shin_r");
  c("sole_r", "foot_r", [3, 0, -10], [14, 4, 5]);
  c("toe_r1", "foot_r", [3, 0, -13], [6, 3, -10]);
  c("toe_r2", "foot_r", [7, 0, -14], [10, 3, -10]);
  c("toe_r3", "foot_r", [11, 0, -13], [14, 3, -10]);
  g("leg_l", [-8, 18, 0], [0, 0, 0], "hull");
  c("thigh_l", "leg_l", [-14, 9, -6], [-3, 20, 6]);
  g("shin_l", [-8.5, 10, 0], [0, 0, 0], "leg_l");
  c("calf_l", "shin_l", [-13, 3, -5], [-4, 11, 5]);
  g("foot_l", [-8.5, 4, 0], [0, 0, 0], "shin_l");
  c("sole_l", "foot_l", [-14, 0, -10], [-3, 4, 5]);
  c("toe_l1", "foot_l", [-14, 0, -13], [-11, 3, -10]);
  c("toe_l2", "foot_l", [-10, 0, -14], [-7, 3, -10]);
  c("toe_l3", "foot_l", [-6, 0, -13], [-3, 3, -10]);

  Canvas.updateAll();
  return { elements: Project.elements.length, groups: Project.groups.length };
})();
