import bpy
import os
import math
import struct
import shutil
from mathutils import Vector, Matrix, Euler, Quaternion

PROJ_DIR = "/home/borichdev/projects/kunigamicalculator"
RAW_DIR = os.path.join(PROJ_DIR, "assets_raw")
PROD_DIR = os.path.join(PROJ_DIR, "prod")
TEX_OUT_DIR = os.path.join(PROD_DIR, "assets", "textures")
MODEL_OUT_PATH = os.path.join(PROD_DIR, "assets", "models", "kunigami.k3d")

os.makedirs(TEX_OUT_DIR, exist_ok=True)
os.makedirs(os.path.dirname(MODEL_OUT_PATH), exist_ok=True)

MAT_INFO = {
    "MT_common_num888":       ("T_common_num888_col.png",        "T_common_num888_sdw.png",        16),
    "MT_commnon_shoeA":       ("T_CH001002_shoe_col.png",        "T_CH001002_shoe_sdw.png",        0),
    "MT_common_innerA":       ("T_common_innerA_col.png",        "T_common_innerA_sdw.png",        0),
    "MT_CH003002_innerOth":   ("T_CH003002_innerOth_col.png",    "T_CH003002_innerOth_sdw.png",    1),
    "MT_commnon_uniformF":    ("T_common_uniformF_col.png",      "T_common_uniformF_sdw.png",      0),
    "MT_CH003004_uniformNumF":("T_CH003004_uniformNumF_col.png", "T_CH003004_uniformNumF_sdw.png", 16),
    "MT_CH003002_hair":       ("T_CH003002_hair_col.png",        "T_CH003002_hair_sdw.png",        2),
    "MT_CH003002_colPalet":   ("T_CH003002_colPalette_col.png",  "T_CH003002_colPalette_sdw.png",  1 | 16),
    "MT_CH003002_ear":        ("T_CH003002_otherFace_col.png",   "T_CH003002_otherFace_sdw.png",   1),
    "MT_CH003002_eye":        ("T_CH003002_eye_col.png",         "T_CH003002_eye_col.png",         1 | 16),
    "MT_CH003002_eyeBrow":    ("T_CH003002_eyeBrow_col.png",     "T_CH003002_eyeBrow_sdw.png",     1 | 16),
    "MT_common_eyeEgo":       ("T_common_eyeEgo_col.png",        "T_common_eyeEgo_col.png",        4 | 8 | 16),
}

# Only copy from RAW_DIR if texture is not already upgraded in TEX_OUT_DIR
copied_tex = set()
for col_t, sdw_t, _ in MAT_INFO.values():
    for tname in (col_t, sdw_t):
        if tname not in copied_tex:
            dst_path = os.path.join(TEX_OUT_DIR, tname)
            if not os.path.exists(dst_path):
                shutil.copy2(os.path.join(RAW_DIR, tname), dst_path)
            copied_tex.add(tname)

arm = bpy.data.objects["Armature"]
bs = bpy.data.objects["mesh_bs"]

# Create Eyelid_M bone in Edit Mode as child of Head_M for natural blinking & eye-squints
bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode='EDIT')
head_eb = arm.data.edit_bones["Head_M"]
lid_eb = arm.data.edit_bones.get("Eyelid_M") or arm.data.edit_bones.new("Eyelid_M")
lid_eb.parent = head_eb
arm_mwi = arm.matrix_world.inverted()
lid_eb.matrix = head_eb.matrix.copy()
lid_eb.head = arm_mwi @ Vector((0.0, -0.065, 1.725))
lid_eb.tail = lid_eb.head + (head_eb.tail - head_eb.head)
lid_eb.roll = head_eb.roll
bpy.ops.object.mode_set(mode='OBJECT')

mw_bs = bs.matrix_world
mwi_bs = mw_bs.inverted()

brow_v_indices = set()
for p in bs.data.polygons:
    mname = bs.data.materials[p.material_index].name if p.material_index < len(bs.data.materials) else ""
    if "eyeBrow" in mname:
        for vi in p.vertices:
            brow_v_indices.add(vi)

# 1. Weight lower jaw using the exact parabolic lip-slit boundary BEFORE sculpting the smile
jaw_vg = bs.vertex_groups.get("Jaw_M") or bs.vertex_groups.new(name="Jaw_M")
lid_vg = bs.vertex_groups.get("Eyelid_M") or bs.vertex_groups.new(name="Eyelid_M")
head_vg = bs.vertex_groups.get("Head_M")

for v in bs.data.vertices:
    w = mw_bs @ v.co
    z_slit = 1.65535 - 10.5 * (w.x * w.x)
    if 1.615 < w.z < z_slit and w.y < -0.072 and abs(w.x) < 0.036:
        if w.y > -0.098 and w.z > 1.6515:
            continue
        fx = max(0.0, min(1.0, (0.0145 - abs(w.x)) / 0.0045))
        fz_bot = max(0.0, min(1.0, (w.z - 1.620) / 0.015))
        fy = max(0.0, min(1.0, (-0.074 - w.y) / 0.012))
        weight = fx * fz_bot * fy * 0.90
        if weight > 0.01:
            jaw_vg.add([v.index], weight, 'REPLACE')
            head_vg.add([v.index], 1.0 - weight, 'REPLACE')

    # Weight upper eyelid and upper eyelash arch to Eyelid_M across full eye width
    if v.index not in brow_v_indices:
        ax = abs(w.x)
        y_back = -0.094 + max(0.0, ax - 0.025) * 0.35
        if 0.013 < ax < 0.059 and 1.7262 < w.z < 1.7376 and w.y < y_back:
            fx_in = max(0.0, min(1.0, (ax - 0.014) / 0.006))
            fx_out = max(0.0, min(1.0, (0.058 - ax) / 0.005))
            fz_top = max(0.0, min(1.0, (1.7372 - w.z) / 0.0038))
            fz_bot = max(0.0, min(1.0, (w.z - 1.7262) / 0.0018))
            l_weight = fx_in * fx_out * fz_top * fz_bot
            if l_weight > 0.01:
                lid_vg.add([v.index], l_weight, 'REPLACE')
                head_vg.add([v.index], 1.0 - l_weight, 'REPLACE')

# 2. Sculpt cheerful, warm smile on mesh_bs and friendly eyebrows
for v in bs.data.vertices:
    w = mw_bs @ v.co
    if 1.640 < w.z < 1.668 and w.y < -0.082 and abs(w.x) < 0.026:
        dz_mouth = (w.z - 1.6548) / 0.0065
        dy_mouth = (w.y - (-0.1025)) / 0.0085
        env = math.exp(-0.5 * (dz_mouth * dz_mouth + dy_mouth * dy_mouth))
        u = min(1.3, abs(w.x) / 0.0125)
        smile_dz = (-0.0005 * (1.0 - u * u) + 0.0045 * (u * u)) * env
        smile_dx = (0.0008 * u * (1.0 if w.x >= 0 else -1.0)) * env
        w.z += smile_dz
        w.x += smile_dx
        v.co = mwi_bs @ w

    if v.index in brow_v_indices:
        inner_factor = max(0.0, 1.0 - abs(w.x) / 0.045)
        w.z += 0.0028 * inner_factor + 0.0010
        v.co = mwi_bs @ w

# Weight eye meshes to Eye_L and Eye_R
for mname, bname in [("mesh_eyeL", "Eye_L"), ("mesh_eyeHilightL", "Eye_L"),
                     ("mesh_eyeR", "Eye_R"), ("mesh_eyeHilightR", "Eye_R")]:
    obj = bpy.data.objects[mname]
    obj.vertex_groups.clear()
    vg = obj.vertex_groups.new(name=bname)
    vg.add(list(range(len(obj.data.vertices))), 1.0, 'REPLACE')

ACTIVE_BONE_NAMES = [
    "Root_M",
    "Hip_L", "Hip_R",
    "Spine1_M", "Spine2_M", "Chest_M",
    "Neck_M", "Head_M", "Jaw_M", "Eyelid_M", "Eye_L", "Eye_R",
    "p_HairFront1_M", "p_HairTopA1_M", "p_HairRearA1_M",
    "Scapula_L", "Shoulder_L", "ShoulderPart1_L", "Elbow_L", "ElbowPart1_L", "Wrist_L",
    "ThumbFinger1_L", "ThumbFinger2_L", "ThumbFinger3_L",
    "IndexFinger1_L", "IndexFinger2_L", "IndexFinger3_L",
    "MiddleFinger1_L", "MiddleFinger2_L", "MiddleFinger3_L",
    "RingFinger1_L", "RingFinger2_L", "RingFinger3_L",
    "PinkyFinger1_L", "PinkyFinger2_L", "PinkyFinger3_L",
    "Scapula_R", "Shoulder_R", "ShoulderPart1_R", "Elbow_R", "ElbowPart1_R", "Wrist_R",
    "ThumbFinger1_R", "ThumbFinger2_R", "ThumbFinger3_R",
    "IndexFinger1_R", "IndexFinger2_R", "IndexFinger3_R",
    "MiddleFinger1_R", "MiddleFinger2_R", "MiddleFinger3_R",
    "RingFinger1_R", "RingFinger2_R", "RingFinger3_R",
    "PinkyFinger1_R", "PinkyFinger2_R", "PinkyFinger3_R",
]
active_idx_map = {name: i for i, name in enumerate(ACTIVE_BONE_NAMES)}

bone_to_active = {}
for b in arm.data.bones:
    if b.name in active_idx_map:
        bone_to_active[b.name] = active_idx_map[b.name]
    elif b.name.startswith("p_HairFront"):
        bone_to_active[b.name] = active_idx_map["p_HairFront1_M"]
    elif b.name.startswith("p_HairTop") or b.name.startswith("p_HaitTop"):
        bone_to_active[b.name] = active_idx_map["p_HairTopA1_M"]
    elif b.name.startswith("p_HairRear"):
        bone_to_active[b.name] = active_idx_map["p_HairRearA1_M"]
    else:
        cur = b.parent
        mapped = 0
        while cur is not None:
            if cur.name in active_idx_map:
                mapped = active_idx_map[cur.name]
                break
            cur = cur.parent
        bone_to_active[b.name] = mapped

active_parents = []
for i, bname in enumerate(ACTIVE_BONE_NAMES):
    b = arm.data.bones[bname]
    cur = b.parent
    p_idx = -1
    while cur is not None:
        if cur.name in active_idx_map:
            p_idx = active_idx_map[cur.name]
            break
        cur = cur.parent
    assert p_idx < i
    active_parents.append(p_idx)

w_rest_list = []
l0_list = []
ibm_list = []
for i, bname in enumerate(ACTIVE_BONE_NAMES):
    b = arm.data.bones[bname]
    w_rest = arm.matrix_world @ b.matrix_local
    w_rest_list.append(w_rest)
    ibm_list.append(w_rest.inverted())
    p_idx = active_parents[i]
    if p_idx < 0:
        l0_list.append(w_rest)
    else:
        l0_list.append(w_rest_list[p_idx].inverted() @ w_rest)

bpy.context.view_layer.objects.active = arm
bpy.ops.object.mode_set(mode='POSE')

def reset_pose():
    for pb in arm.pose.bones:
        pb.rotation_mode = 'QUATERNION'
        pb.rotation_quaternion = (1, 0, 0, 0)
        pb.location = (0, 0, 0)
        pb.scale = (1, 1, 1)
    bpy.context.view_layer.update()

def set_bone_world_rot(bname, rx_deg, ry_deg, rz_deg):
    pb = arm.pose.bones[bname]
    rw = Euler((math.radians(rx_deg), math.radians(ry_deg), math.radians(rz_deg)), 'XYZ').to_matrix()
    arm_rot = arm.matrix_world.to_3x3().normalized()
    rest_w = (arm.matrix_world @ pb.bone.matrix_local).to_3x3().normalized()
    target_w = rw @ rest_w
    m = pb.matrix.copy()
    pb.matrix = Matrix.Translation(m.to_translation()) @ (arm_rot.transposed() @ target_w).to_4x4()
    bpy.context.view_layer.update()

def aim_bone_world(bname, dir_vec, palm_vec, is_right):
    d = Vector(dir_vec).normalized()
    p = Vector(palm_vec).normalized()
    p = (p - d * d.dot(p)).normalized()
    if not is_right:
        x_col = -d
        z_col = p
        y_col = z_col.cross(x_col).normalized()
        z_col = x_col.cross(y_col).normalized()
    else:
        x_col = d
        z_col = -p
        y_col = z_col.cross(x_col).normalized()
        z_col = x_col.cross(y_col).normalized()
    target_w = Matrix((x_col, y_col, z_col)).transposed()
    pb = arm.pose.bones[bname]
    arm_rot = arm.matrix_world.to_3x3().normalized()
    m = pb.matrix.copy()
    pb.matrix = Matrix.Translation(m.to_translation()) @ (arm_rot.transposed() @ target_w).to_4x4()
    bpy.context.view_layer.update()

def aim_arm(side, u_dir, u_palm, f_dir, f_palm, w_dir, w_palm):
    is_r = (side == 'R')
    aim_bone_world(f'Shoulder_{side}', u_dir, u_palm, is_r)
    aim_bone_world(f'ShoulderPart1_{side}', u_dir, (Vector(u_palm) + Vector(f_palm))*0.5, is_r)
    aim_bone_world(f'Elbow_{side}', f_dir, f_palm, is_r)
    aim_bone_world(f'ElbowPart1_{side}', f_dir, (Vector(f_palm) + Vector(w_palm))*0.5, is_r)
    aim_bone_world(f'Wrist_{side}', w_dir, w_palm, is_r)

def set_fingers(side, thumb, index, middle, ring, pinky, spreads=(0,0,0,0)):
    fnames = ['IndexFinger', 'MiddleFinger', 'RingFinger', 'PinkyFinger']
    curls = [index, middle, ring, pinky]
    for fname, cval, sp in zip(fnames, curls, spreads):
        for i, mult in [(1, 0.85), (2, 1.15), (3, 0.85)]:
            pb = arm.pose.bones[f'{fname}{i}_{side}']
            pb.rotation_mode = 'XYZ'
            s = math.radians(sp) if i == 1 else 0.0
            c = math.radians(cval * 82.0 * mult)
            pb.rotation_euler = Euler((0, -c, s), 'XYZ')
    t1 = (25 * thumb, -42 * thumb, -35 * thumb)
    t2 = (0, -48 * thumb, 0)
    t3 = (0, -35 * thumb, 0)
    for i, ang in [(1, t1), (2, t2), (3, t3)]:
        pb = arm.pose.bones[f'ThumbFinger{i}_{side}']
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler([math.radians(a) for a in ang], 'XYZ')
    bpy.context.view_layer.update()

def arm_relaxed_L():
    aim_arm('L', (0.20, 0.04, -0.98), (-0.85, -0.5, 0.0), (-0.06, -0.22, -0.97), (-0.8, -0.6, 0.0), (-0.04, -0.14, -0.99), (-0.85, -0.5, 0.0))
    set_fingers('L', 0.35, 0.45, 0.55, 0.65, 0.70)

def arm_hip_L():
    aim_arm('L', (0.45, 0.06, -0.89), (-0.5, -0.8, -0.2), (-0.52, -0.35, -0.77), (-0.4, -0.8, 0.3), (-0.38, -0.25, -0.89), (-0.5, -0.8, 0.2))
    set_fingers('L', 0.6, 0.75, 0.8, 0.85, 0.9)

def arm_relaxed_R():
    aim_arm('R', (-0.20, 0.04, -0.98), (0.85, -0.5, 0.0), (0.06, -0.22, -0.97), (0.8, -0.6, 0.0), (0.04, -0.14, -0.99), (0.85, -0.5, 0.0))
    set_fingers('R', 0.35, 0.45, 0.55, 0.65, 0.70)

def arm_show_R(tilt_x=0.05):
    aim_arm('R', (-0.26, -0.36, -0.89), (0.4, -0.9, 0.0), (0.22, -0.46, 0.86), (0.0, -1.0, 0.15), (tilt_x, -0.10, 0.99), (0.0, -1.0, -0.10))

def arm_show_L(tilt_x=-0.05):
    aim_arm('L', (0.26, -0.36, -0.89), (-0.4, -0.9, 0.0), (-0.22, -0.46, 0.86), (0.0, -1.0, 0.15), (tilt_x, -0.10, 0.99), (0.0, -1.0, -0.10))

POSE_LIST = [
    "IDLE",
    "DIGIT_0", "DIGIT_1", "DIGIT_2", "DIGIT_3", "DIGIT_4",
    "DIGIT_5", "DIGIT_6", "DIGIT_7", "DIGIT_8", "DIGIT_9",
    "OP_PLUS", "OP_MINUS", "OP_MUL", "OP_DIV",
    "EQUALS_EGO", "BACKSPACE", "CLEAR",
    "EASTER_67_A", "EASTER_67_B", "EASTER_87", "EASTER_50", "EASTER_993", "EASTER_52"
]

def build_pose(name):
    reset_pose()
    if name == 'IDLE':
        set_bone_world_rot('Spine2_M', 0, 1, -2)
        set_bone_world_rot('Chest_M', -2, -1, 3)
        set_bone_world_rot('Head_M', 2, -1, -2)
        arm_hip_L()
        arm_relaxed_R()
    elif name == 'DIGIT_0':
        set_bone_world_rot('Chest_M', -2, 0, 4)
        set_bone_world_rot('Head_M', 3, -3, -3)
        arm_hip_L()
        arm_show_R(0.04)
        set_fingers('R', 0.85, 0.68, 0.10, 0.05, 0.0, spreads=(0, 4, -4, -10))
    elif name == 'DIGIT_1':
        set_bone_world_rot('Chest_M', -2, 1, 4)
        set_bone_world_rot('Head_M', 2, -3, -3)
        arm_hip_L()
        arm_show_R(0.04)
        set_fingers('R', 0.92, 0.0, 0.95, 0.96, 0.98)
    elif name == 'DIGIT_2':
        set_bone_world_rot('Chest_M', -2, -2, 3)
        set_bone_world_rot('Head_M', 3, -4, 2)
        arm_relaxed_L()
        arm_show_R(0.05)
        set_fingers('R', 0.95, 0.0, 0.0, 0.95, 0.98, spreads=(10, -10, 0, 0))
    elif name == 'DIGIT_3':
        set_bone_world_rot('Chest_M', -2, 2, -2)
        set_bone_world_rot('Head_M', 2, 3, 2)
        arm_hip_L()
        arm_show_R(0.05)
        set_fingers('R', 0.95, 0.0, 0.0, 0.0, 0.98, spreads=(11, 0, -11, 0))
    elif name == 'DIGIT_4':
        set_bone_world_rot('Chest_M', -2, -1, 3)
        set_bone_world_rot('Head_M', 3, -2, -2)
        arm_relaxed_L()
        arm_show_R(0.05)
        set_fingers('R', 0.95, 0.0, 0.0, 0.0, 0.0, spreads=(12, 4, -4, -12))
    elif name == 'DIGIT_5':
        set_bone_world_rot('Chest_M', -3, 2, 5)
        set_bone_world_rot('Head_M', 4, -3, -4)
        arm_hip_L()
        arm_show_R(0.06)
        set_fingers('R', 0.0, 0.0, 0.0, 0.0, 0.0, spreads=(14, 5, -5, -14))
    elif name == 'DIGIT_6':
        set_bone_world_rot('Chest_M', -2, 0, 0)
        set_bone_world_rot('Head_M', 4, -4, 0)
        arm_show_R(0.05)
        set_fingers('R', 0.95, 0.0, 0.0, 0.0, 0.98, spreads=(11, 0, -11, 0))
        arm_show_L(-0.05)
        set_fingers('L', 0.95, 0.0, 0.0, 0.0, 0.98, spreads=(11, 0, -11, 0))
    elif name == 'DIGIT_7':
        set_bone_world_rot('Chest_M', -2, 2, 2)
        set_bone_world_rot('Head_M', 3, 3, -2)
        arm_show_R(0.05)
        set_fingers('R', 0.0, 0.0, 0.0, 0.0, 0.0, spreads=(12, 4, -4, -12))
        arm_show_L(-0.05)
        set_fingers('L', 0.95, 0.0, 0.0, 0.95, 0.98, spreads=(10, -10, 0, 0))
    elif name == 'DIGIT_8':
        set_bone_world_rot('Chest_M', -2, -2, -2)
        set_bone_world_rot('Head_M', 3, -3, 2)
        arm_show_R(0.05)
        set_fingers('R', 0.0, 0.0, 0.0, 0.0, 0.0, spreads=(12, 4, -4, -12))
        arm_show_L(-0.05)
        set_fingers('L', 0.95, 0.0, 0.0, 0.0, 0.98, spreads=(11, 0, -11, 0))
    elif name == 'DIGIT_9':
        set_bone_world_rot('Chest_M', -3, 1, 0)
        set_bone_world_rot('Head_M', 4, 2, 0)
        arm_show_R(0.05)
        set_fingers('R', 0.0, 0.0, 0.0, 0.0, 0.0, spreads=(12, 4, -4, -12))
        arm_show_L(-0.05)
        set_fingers('L', 0.95, 0.0, 0.0, 0.0, 0.0, spreads=(12, 4, -4, -12))
    elif name == 'OP_PLUS':
        set_bone_world_rot('Chest_M', -2, 0, 0)
        set_bone_world_rot('Head_M', 3, -2, 0)
        aim_arm('R', (-0.15, -0.68, -0.71), (0.7, -0.7, 0.0), (0.68, -0.22, 0.70), (0.0, -1.0, 0.1), (0.68, -0.18, 0.71), (0.0, -1.0, 0.1))
        set_fingers('R', 0.3, 0.0, 0.0, 0.0, 0.0)
        aim_arm('L', (0.15, -0.72, -0.68), (-0.7, -0.7, 0.0), (-0.68, -0.26, 0.68), (0.0, -1.0, 0.1), (-0.68, -0.22, 0.70), (0.0, -1.0, 0.1))
        set_fingers('L', 0.3, 0.0, 0.0, 0.0, 0.0)
    elif name == 'OP_MINUS':
        set_bone_world_rot('Chest_M', -2, 0, 3)
        set_bone_world_rot('Head_M', 2, 0, -3)
        arm_hip_L()
        aim_arm('R', (-0.25, -0.55, -0.80), (0.0, -1.0, 0.2), (0.98, -0.18, 0.04), (0.0, -1.0, 0.1), (0.99, -0.12, 0.02), (0.0, -1.0, 0.0))
        set_fingers('R', 0.4, 0.0, 0.0, 0.0, 0.0)
    elif name == 'OP_MUL':
        set_bone_world_rot('Chest_M', 3, 0, 0)
        set_bone_world_rot('Head_M', 5, 0, 0)
        aim_arm('R', (-0.12, -0.68, -0.72), (0.7, -0.7, 0.0), (0.65, -0.22, 0.72), (0.0, -1.0, 0.1), (0.65, -0.18, 0.74), (0.0, -1.0, 0.1))
        set_fingers('R', 0.9, 0.95, 0.95, 0.95, 0.95)
        aim_arm('L', (0.12, -0.72, -0.68), (-0.7, -0.7, 0.0), (-0.65, -0.26, 0.70), (0.0, -1.0, 0.1), (-0.65, -0.22, 0.72), (0.0, -1.0, 0.1))
        set_fingers('L', 0.9, 0.95, 0.95, 0.95, 0.95)
    elif name == 'OP_DIV':
        set_bone_world_rot('Chest_M', -2, 2, -4)
        set_bone_world_rot('Head_M', 3, -3, 4)
        arm_hip_L()
        aim_arm('R', (-0.20, -0.60, -0.77), (0.5, -0.8, 0.2), (0.68, -0.20, 0.70), (0.0, -1.0, 0.1), (0.68, -0.15, 0.72), (0.0, -1.0, 0.1))
        set_fingers('R', 0.4, 0.0, 0.0, 0.0, 0.0)
    elif name == 'EQUALS_EGO':
        set_bone_world_rot('Spine2_M', -4, 0, 0)
        set_bone_world_rot('Chest_M', -5, 0, 0)
        set_bone_world_rot('Head_M', 6, -2, 0)
        aim_arm('R', (-0.36, -0.32, -0.87), (0.3, -0.9, 0.1), (0.18, -0.45, 0.87), (0.0, -1.0, 0.1), (-0.04, -0.15, 0.98), (0.1, -1.0, -0.1))
        set_fingers('R', 0.05, 0.12, 0.12, 0.12, 0.12, spreads=(12, 4, -4, -12))
        aim_arm('L', (0.36, -0.32, -0.87), (-0.3, -0.9, 0.1), (-0.18, -0.45, 0.87), (0.0, -1.0, 0.1), (0.04, -0.15, 0.98), (-0.1, -1.0, -0.1))
        set_fingers('L', 0.05, 0.12, 0.12, 0.12, 0.12, spreads=(12, 4, -4, -12))
    elif name == 'BACKSPACE':
        set_bone_world_rot('Spine2_M', -5, 0, 0)
        set_bone_world_rot('Chest_M', -6, 0, 0)
        set_bone_world_rot('Head_M', -6, 4, 0)
        aim_arm('R', (-0.74, -0.15, 0.65), (0.0, -1.0, 0.2), (0.65, 0.35, 0.67), (0.2, -0.9, -0.3), (0.78, 0.22, 0.58), (0.0, -0.95, -0.3))
        set_fingers('R', 0.3, 0.25, 0.25, 0.25, 0.25)
        aim_arm('L', (0.74, -0.15, 0.65), (0.0, -1.0, 0.2), (-0.65, 0.35, 0.67), (-0.2, -0.9, -0.3), (-0.78, 0.22, 0.58), (0.0, -0.95, -0.3))
        set_fingers('L', 0.3, 0.25, 0.25, 0.25, 0.25)
    elif name == 'CLEAR':
        set_bone_world_rot('Chest_M', -4, 0, 0)
        set_bone_world_rot('Head_M', 4, 0, 0)
        aim_arm('R', (-0.38, -0.25, -0.89), (0.4, -0.9, 0.0), (0.25, -0.65, 0.72), (-0.7, -0.7, 0.0), (0.20, -0.55, 0.81), (-0.8, -0.6, 0.0))
        set_fingers('R', 0.9, 0.95, 0.95, 0.95, 0.95)
        aim_arm('L', (0.38, -0.25, -0.89), (-0.4, -0.9, 0.0), (-0.25, -0.65, 0.72), (0.7, -0.7, 0.0), (-0.20, -0.55, 0.81), (0.8, -0.6, 0.0))
        set_fingers('L', 0.9, 0.95, 0.95, 0.95, 0.95)
    elif name == 'EASTER_67_A':
        set_bone_world_rot('Spine1_M', 0, -4, 0)
        set_bone_world_rot('Chest_M', -3, -6, 4)
        set_bone_world_rot('Head_M', 4, 6, -4)
        aim_arm('R', (-0.28, -0.35, -0.89), (0.0, -0.3, 1.0), (0.12, -0.85, 0.51), (0.0, -0.2, 1.0), (0.05, -0.95, 0.30), (0.0, -0.1, 1.0))
        set_fingers('R', 0.1, 0.1, 0.1, 0.1, 0.1, spreads=(10, 3, -3, -10))
        aim_arm('L', (0.28, -0.45, -0.85), (0.0, -0.3, 1.0), (-0.12, -0.55, 0.82), (0.0, -0.2, 1.0), (-0.05, -0.90, 0.42), (0.0, -0.1, 1.0))
        set_fingers('L', 0.1, 0.1, 0.1, 0.1, 0.1, spreads=(10, 3, -3, -10))
    elif name == 'EASTER_67_B':
        set_bone_world_rot('Spine1_M', 0, 4, 0)
        set_bone_world_rot('Chest_M', -3, 6, -4)
        set_bone_world_rot('Head_M', 4, -6, 4)
        aim_arm('R', (-0.28, -0.45, -0.85), (0.0, -0.3, 1.0), (0.12, -0.55, 0.82), (0.0, -0.2, 1.0), (0.05, -0.90, 0.42), (0.0, -0.1, 1.0))
        set_fingers('R', 0.1, 0.1, 0.1, 0.1, 0.1, spreads=(10, 3, -3, -10))
        aim_arm('L', (0.28, -0.35, -0.89), (0.0, -0.3, 1.0), (-0.12, -0.85, 0.51), (0.0, -0.2, 1.0), (-0.05, -0.95, 0.30), (0.0, -0.1, 1.0))
        set_fingers('L', 0.1, 0.1, 0.1, 0.1, 0.1, spreads=(10, 3, -3, -10))
    elif name == 'EASTER_87':
        set_bone_world_rot('Spine2_M', 6, 0, 0)
        set_bone_world_rot('Chest_M', 8, 0, 0)
        set_bone_world_rot('Head_M', 8, 0, 0)
        arm.pose.bones['Jaw_M'].rotation_mode = 'XYZ'
        arm.pose.bones['Jaw_M'].rotation_euler = Euler((0, 0, math.radians(18)), 'XYZ')
        aim_arm('R', (-0.52, -0.55, -0.65), (0.7, -0.7, 0.0), (0.35, -0.15, 0.92), (0.9, -0.4, 0.0), (0.20, 0.05, 0.98), (0.95, -0.3, 0.0))
        set_fingers('R', 0.0, 0.05, 0.05, 0.05, 0.05, spreads=(14, 5, -5, -14))
        aim_arm('L', (0.52, -0.55, -0.65), (-0.7, -0.7, 0.0), (-0.35, -0.15, 0.92), (-0.9, -0.4, 0.0), (-0.20, 0.05, 0.98), (-0.95, -0.3, 0.0))
        set_fingers('L', 0.0, 0.05, 0.05, 0.05, 0.05, spreads=(14, 5, -5, -14))
        bpy.context.view_layer.update()
    elif name == 'EASTER_50':
        set_bone_world_rot('Chest_M', -5, -3, 5)
        set_bone_world_rot('Head_M', 4, 3, -4)
        aim_arm('R', (-0.35, -0.45, -0.82), (0.5, -0.8, 0.0), (0.62, -0.35, 0.70), (0.0, 0.9, 0.4), (0.75, -0.15, 0.64), (0.0, 0.95, 0.3))
        set_fingers('R', 0.0, 0.95, 0.95, 0.95, 0.95)
        aim_arm('L', (0.45, -0.25, 0.85), (-0.3, -0.9, 0.0), (0.12, -0.20, 0.97), (0.0, -1.0, 0.0), (0.05, -0.10, 0.99), (0.0, -1.0, 0.0))
        set_fingers('L', 0.9, 0.95, 0.95, 0.95, 0.95)
    elif name == 'EASTER_993':
        set_bone_world_rot('Chest_M', 4, 0, 0)
        set_bone_world_rot('Head_M', 10, -8, 0)
        aim_arm('R', (-0.25, -0.60, -0.76), (0.5, -0.8, 0.0), (0.38, -0.35, 0.85), (0.0, 0.9, 0.4), (0.15, -0.20, 0.97), (0.0, 0.95, 0.3))
        set_fingers('R', 0.6, 0.85, 0.15, 0.15, 0.15)
        aim_arm('L', (0.25, -0.62, -0.74), (-0.5, -0.8, 0.0), (-0.42, -0.32, 0.85), (0.0, -0.9, -0.4), (-0.55, -0.20, 0.81), (0.0, -0.95, -0.3))
        set_fingers('L', 0.5, 0.7, 0.7, 0.7, 0.7)
    elif name == 'EASTER_52':
        # "52" Swagger Pose: Right hand raised high showing '5', Left hand angled out showing '2' V-sign!
        set_bone_world_rot('Spine2_M', -4, 0, 2)
        set_bone_world_rot('Chest_M', -5, -3, 5)
        set_bone_world_rot('Head_M', 5, -5, 4)
        aim_arm('R', (-0.34, -0.30, -0.89), (0.35, -0.9, 0.0), (0.18, -0.35, 0.92), (0.0, -1.0, 0.1), (0.08, -0.08, 0.99), (0.0, -1.0, -0.1))
        set_fingers('R', 0.0, 0.0, 0.0, 0.0, 0.0, spreads=(15, 6, -6, -15))
        aim_arm('L', (0.32, -0.40, -0.86), (-0.35, -0.9, 0.0), (-0.28, -0.48, 0.83), (0.0, -1.0, 0.15), (-0.18, -0.15, 0.97), (0.0, -1.0, -0.1))
        set_fingers('L', 0.95, 0.0, 0.0, 0.95, 0.98, spreads=(12, -12, 0, 0))

all_pose_quats = []
for pname in POSE_LIST:
    build_pose(pname)
    quats = []
    for i, bname in enumerate(ACTIVE_BONE_NAMES):
        pb = arm.pose.bones[bname]
        w_actual = arm.matrix_world @ pb.matrix
        p_idx = active_parents[i]
        if p_idx < 0:
            w_parent_actual = Matrix.Identity(4)
        else:
            p_bname = ACTIVE_BONE_NAMES[p_idx]
            w_parent_actual = arm.matrix_world @ arm.pose.bones[p_bname].matrix
        q = (l0_list[i].inverted() @ w_parent_actual.inverted() @ w_actual).to_quaternion().normalized()
        quats.append((q.x, q.y, q.z, q.w))
    all_pose_quats.append(quats)

reset_pose()
bpy.ops.object.mode_set(mode='OBJECT')

MESH_ORDER = [
    "mesh_body",
    "mesh_body.001",
    "mesh_bs",
    "mesh_eyeL", "mesh_eyeR",
    "mesh_eyeHilightL", "mesh_eyeHilightR",
    "mesh_eyeEgoL", "mesh_eyeEgoR",
]

# Materials to upgrade with 1-to-4 Curved Phong Tessellation (smooth hands, fingers, face, uniform)
TESSELLATE_MATS = {
    "MT_CH003002_innerOth",
    "MT_commnon_uniformF",
    "MT_CH003002_colPalet",
    "MT_CH003002_ear",
}

def normalize_top4(w_dict):
    top4 = sorted(w_dict.items(), key=lambda x: x[1], reverse=True)[:4]
    while len(top4) < 4:
        top4.append((0, 0.0))
    w_sum = sum(w for _, w in top4)
    if w_sum > 1e-6:
        return [(j, w / w_sum) for j, w in top4]
    return [(0, 1.0), (0, 0.0), (0, 0.0), (0, 0.0)]

def compute_shading_normal(obj_name, wp, geom_n):
    if obj_name == "mesh_bs" and wp.z > 1.622 and wp.y < -0.070:
        return Vector((wp.x * 3.0, -0.96, 0.20)).normalized()
    return geom_n

submeshes = []
for obj_name in MESH_ORDER:
    obj = bpy.data.objects[obj_name]
    mesh = obj.data
    mesh.calc_loop_triangles()
    uv_layer = mesh.uv_layers[0].data

    vg_to_active = {}
    for vg in obj.vertex_groups:
        vg_to_active[vg.index] = bone_to_active.get(vg.name, 0)

    mw = obj.matrix_world
    mw_norm = mw.to_3x3().inverted().transposed()

    # Step A: Compute true geometric vertex normals from triangle face normals so back-faces never have inverted normals
    raw_wp = [mw @ v.co for v in mesh.vertices]
    geom_accum = [Vector((0.0, 0.0, 0.0)) for _ in mesh.vertices]
    for tri in mesh.loop_triangles:
        v0, v1, v2 = tri.vertices
        fn = (raw_wp[v1] - raw_wp[v0]).cross(raw_wp[v2] - raw_wp[v0])
        geom_accum[v0] += fn
        geom_accum[v1] += fn
        geom_accum[v2] += fn

    pos_groups = {}
    raw_wn = []
    for v in mesh.vertices:
        wp = raw_wp[v.index]
        gn = geom_accum[v.index]
        wn = gn.normalized() if gn.length > 1e-7 else (mw_norm @ v.normal).normalized()
        raw_wn.append(wn)
        pkey = (round(wp.x, 4), round(wp.y, 4), round(wp.z, 4))
        pos_groups.setdefault(pkey, []).append(v.index)

    v_skin = []
    for v in mesh.vertices:
        wp = raw_wp[v.index]
        wn_own = raw_wn[v.index]
        pkey = (round(wp.x, 4), round(wp.y, 4), round(wp.z, 4))
        acc_n = Vector((0.0, 0.0, 0.0))
        for other_vi in pos_groups[pkey]:
            other_n = raw_wn[other_vi]
            if wn_own.dot(other_n) > 0.35:
                acc_n += other_n
        geom_n = acc_n.normalized() if acc_n.length > 1e-5 else wn_own
        shade_n = compute_shading_normal(obj_name, wp, geom_n)

        w_accum = {}
        for g in v.groups:
            if g.weight > 1e-4:
                a_idx = vg_to_active.get(g.group, 0)
                w_accum[a_idx] = w_accum.get(a_idx, 0.0) + g.weight
        if not w_accum:
            w_accum[active_idx_map["Head_M"]] = 1.0
        top4 = normalize_top4(w_accum)
        v_skin.append((wp, geom_n, shade_n, w_accum, top4))

    tris_by_mat = {}
    for tri in mesh.loop_triangles:
        m_idx = tri.material_index
        m_name = mesh.materials[m_idx].name if m_idx < len(mesh.materials) and mesh.materials[m_idx] else ""
        if m_name not in MAT_INFO:
            continue
        tris_by_mat.setdefault(m_name, []).append(tri)

    for m_name, tris in tris_by_mat.items():
        col_tex, sdw_tex, flags = MAT_INFO[m_name]
        if "Hilight" in obj_name:
            flags |= 16
        vert_map = {}
        verts_list = []
        indices_list = []

        def get_or_add_vert(key, wp, shade_n, uv_x, uv_y, top4):
            idx = vert_map.get(key)
            if idx is None:
                idx = len(verts_list)
                vert_map[key] = idx
                verts_list.append((
                    wp.x, wp.y, wp.z,
                    shade_n.x, shade_n.y, shade_n.z,
                    uv_x, uv_y,
                    top4[0][0], top4[1][0], top4[2][0], top4[3][0],
                    top4[0][1], top4[1][1], top4[2][1], top4[3][1]
                ))
            return idx

        do_tess = (m_name in TESSELLATE_MATS) and (obj_name in ("mesh_body", "mesh_bs"))
        for tri in tris:
            v0, v1, v2 = tri.vertices
            l0, l1, l2 = tri.loops
            uv0 = uv_layer[l0].uv
            uv1 = uv_layer[l1].uv
            uv2 = uv_layer[l2].uv

            if not do_tess:
                for v_idx, uv in ((v0, uv0), (v1, uv1), (v2, uv2)):
                    wp, _, shade_n, _, top4 = v_skin[v_idx]
                    ux, uy = uv.x, 1.0 - uv.y
                    key = ("V", v_idx, round(ux, 5), round(uy, 5))
                    indices_list.append(get_or_add_vert(key, wp, shade_n, ux, uy, top4))
            else:
                # 1-to-4 Curved Phong Tessellation with seamless spatial edge keys
                corners = []
                for v_idx, uv in ((v0, uv0), (v1, uv1), (v2, uv2)):
                    wp, geom_n, shade_n, w_dict, top4 = v_skin[v_idx]
                    ux, uy = uv.x, 1.0 - uv.y
                    key = ("V", v_idx, round(ux, 5), round(uy, 5))
                    c_idx = get_or_add_vert(key, wp, shade_n, ux, uy, top4)
                    corners.append((c_idx, wp, geom_n, w_dict, ux, uy))

                mids = []
                for (ia, ib) in ((0, 1), (1, 2), (2, 0)):
                    _, pa, na, wa, uxa, uya = corners[ia]
                    _, pb, nb, wb, uxb, uyb = corners[ib]
                    mid_lin = (pa + pb) * 0.5
                    # Phong tangent-plane projection (alpha = 0.50)
                    proj_a = (mid_lin - pa).dot(na) * na
                    proj_b = (mid_lin - pb).dot(nb) * nb
                    wp_mid = mid_lin - 0.125 * (proj_a + proj_b)
                    n_mid = (na + nb).normalized()
                    shade_mid = compute_shading_normal(obj_name, wp_mid, n_mid)
                    ux_m = 0.5 * (uxa + uxb)
                    uy_m = 0.5 * (uya + uyb)
                    w_mid = {}
                    for k, val in wa.items():
                        w_mid[k] = w_mid.get(k, 0.0) + 0.5 * val
                    for k, val in wb.items():
                        w_mid[k] = w_mid.get(k, 0.0) + 0.5 * val
                    top4_mid = normalize_top4(w_mid)
                    ekey = (
                        "E",
                        round(mid_lin.x, 4), round(mid_lin.y, 4), round(mid_lin.z, 4),
                        round(ux_m, 5), round(uy_m, 5)
                    )
                    m_idx_out = get_or_add_vert(ekey, wp_mid, shade_mid, ux_m, uy_m, top4_mid)
                    mids.append(m_idx_out)

                c0, c1, c2 = corners[0][0], corners[1][0], corners[2][0]
                m01, m12, m20 = mids[0], mids[1], mids[2]
                indices_list.extend([
                    c0, m01, m20,
                    m01, c1, m12,
                    m20, m12, c2,
                    m01, m12, m20
                ])

        submeshes.append((col_tex, sdw_tex, flags, verts_list, indices_list))

def pack_mat4_col_major(m):
    vals = []
    for col in range(4):
        for row in range(4):
            vals.append(float(m[row][col]))
    return struct.pack("<16f", *vals)

def pack_str(s):
    b = s.encode("utf-8")
    return struct.pack("<H", len(b)) + b

with open(MODEL_OUT_PATH, "wb") as f:
    f.write(b"K3D1")
    f.write(struct.pack("<III", len(ACTIVE_BONE_NAMES), len(POSE_LIST), len(submeshes)))
    special_names = [
        "Spine1_M", "Spine2_M", "Chest_M", "Neck_M", "Head_M",
        "Jaw_M", "Eyelid_M", "Eye_L", "Eye_R",
        "p_HairFront1_M", "p_HairTopA1_M", "p_HairRearA1_M"
    ]
    for sname in special_names:
        f.write(struct.pack("<i", active_idx_map[sname]))
    for i in range(len(ACTIVE_BONE_NAMES)):
        f.write(struct.pack("<i", active_parents[i]))
        f.write(pack_mat4_col_major(l0_list[i]))
        f.write(pack_mat4_col_major(ibm_list[i]))
    for p_idx, pname in enumerate(POSE_LIST):
        f.write(pack_str(pname))
        for qx, qy, qz, qw in all_pose_quats[p_idx]:
            f.write(struct.pack("<4f", qx, qy, qz, qw))
    for col_tex, sdw_tex, flags, verts_list, indices_list in submeshes:
        f.write(pack_str(col_tex))
        f.write(pack_str(sdw_tex))
        f.write(struct.pack("<III", flags, len(verts_list), len(indices_list)))
        for v in verts_list:
            f.write(struct.pack("<8f4B4f", *v))
        f.write(struct.pack(f"<{len(indices_list)}H", *indices_list))

total_verts = sum(len(sm[3]) for sm in submeshes)
total_tris = sum(len(sm[4]) // 3 for sm in submeshes)
print(f"Exported {MODEL_OUT_PATH}: {os.path.getsize(MODEL_OUT_PATH)} bytes, {len(submeshes)} submeshes, {total_verts} verts, {total_tris} tris, {len(ACTIVE_BONE_NAMES)} bones, {len(POSE_LIST)} poses.")
