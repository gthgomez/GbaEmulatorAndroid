# GbaEmulatorAndroid — QA Checklist

---

## 1. JNI/CMake Bridge

- [ ] Native library loads
- [ ] Self-tests pass
- [ ] Step budgets match GBA_Emulator C++ core
- [ ] Error handling propagates correctly

**Pass criteria: 4/4**

---

## 2. ROM Loading

- [ ] SAF file picker opens
- [ ] GBA header validated (Nintendo logo check)
- [ ] Invalid ROMs rejected with message
- [ ] Valid ROM loads successfully
- [ ] Large ROMs (32MB) handled

**Pass criteria: 5/5**

---

## 3. Emulation

- [ ] SurfaceView framebuffer renders
- [ ] Oboe audio ring-buffer plays
- [ ] Input maps A/B/Start/Select/D-pad/L/R correctly
- [ ] Frame rate stable
- [ ] Save/load state works

**Pass criteria: 5/5**

---

## 4. Save System

- [ ] Cartridge save exports to file
- [ ] Cartridge save imports from file
- [ ] Save-state exports
- [ ] Save-state restores correctly

**Pass criteria: 4/4**

---

## 5. UI

- [ ] Game screen renders
- [ ] Debug overlay toggle works
- [ ] Settings persist
- [ ] Back button exits gracefully

**Pass criteria: 4/4**

---

## 6. Permissions

- [ ] SAF document access only
- [ ] No broad storage

**Pass criteria: 2/2**

---

## 7. Go / No-Go Gate

**Ship when all items pass.**

- [ ] No ROM/BIOS/assets in repo
- [ ] Self-tests match C++ step budgets
- [ ] Device soak checklist referenced
