"""Full-size output regressions: stairs, crossings, all anchors and real fluids.

These tests re-open compressed output with the independent decoder. They do not
substitute for native player, entity, fluid-update or block-light acceptance.
"""
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest

from tools.arenas.geometry import build_geometry
from tools.arenas.volume import Volume
from tools.arenas.audit import audit_arena, readback


ROOT=Path(__file__).resolve().parents[1]
BRIEF=json.loads((ROOT/"collaboration/arena-build-orchestration/arena-brief.json").read_text())


@unittest.skipUnless(importlib.util.find_spec("numpy"),"private NumPy audit dependency required")
class FullSizeGeometryTests(unittest.TestCase):
    def check_site(self, kind):
        spec=next(s for s in BRIEF["arenas"] if "_"+kind+"_" in s["id"])
        with tempfile.TemporaryDirectory(prefix="arena-geometry-test-") as temp:
            directory=Path(temp)
            with Volume(directory/"scratch") as volume:
                geometry=build_geometry(spec,volume,712)
                schematic=directory/"site.schematic"
                digest=volume.export(schematic,spec["proposed_world_min"])
                palette={}
                for block,meta in volume.used:
                    palette.setdefault(str(block),{"metadata":[]})["metadata"].append(meta)
                row={"id":spec["id"],"sha256":digest,"schematic":schematic.name,
                     "world_min":spec["proposed_world_min"],"anchors":geometry["anchors"],
                     "geometry":geometry,"palette":palette,"layers":dict(volume.layers),
                     "layer_checks":volume.layer_checks(required=geometry["structural_layers"])}
            report=audit_arena(row,directory)
            self.assertEqual("PASS",report["status"],json.dumps({
                "failures":report["failures"],
                "routes":{k:v for k,v in report["routes"].items() if v["status"]!="PASS"},
                "stadium":report.get("stadium_geometry",{}),
                "layer_checks":report.get("layer_checks",{})},indent=2))
            self.assertEqual([500,250,500],report["dimensions"])
            self.assertEqual([0,0,0],report["occupied_min"])
            self.assertEqual([499,249,499],report["occupied_max"])
            self.assertEqual([500,250,500],geometry["stadium_dimensions"])
            architecture=report["stadium_geometry"]
            self.assertEqual("PASS",architecture["status"])
            self.assertEqual([0,0,0],architecture["occupied_min"])
            self.assertEqual([499,249,499],architecture["occupied_max"])
            self.assertTrue(all(n>=50000 for n in architecture["outer_band_counts"].values()))
            self.assertTrue(all(n>=100000 for n in architecture["quadrant_counts"].values()))
            self.assertTrue(all(n>=500 for n in architecture["extreme_plane_counts"].values()))
            self.assertGreaterEqual(architecture["upper50_non_air"],10000)
            self.assertEqual(0,report["fluid_failure_count"])
            self.assertEqual(0,report["gravity_failure_count"])
            self.assertEqual({},report["walking_network"]["unreachable_anchors"])
            self.assertGreaterEqual(len(geometry["rooms"]),6)
            self.assertGreater(geometry["walking_stairs"]["count"],100)
            self.assertGreaterEqual(len(geometry["containers"]),4)
            self.assertEqual(len(geometry["containers"]),report["tile_count"])
            self.assertEqual(19,geometry["route_specs"]["giant_gateway"]["width"])
            self.assertEqual(24,geometry["route_specs"]["giant_gateway"]["clearance"])
            for name in ("entry","prep","checkpoint","boss","reward","exit"):
                self.assertEqual(spec[name+"_local"],geometry["anchors"][name])
            with readback(schematic) as volume:
                for point in geometry["mechanic_nodes"]:
                    self.assertEqual(41,volume.block(*point))
                for container in geometry["containers"]:
                    self.assertEqual(54,volume.block(*container["position"]))
                    self.assertEqual(2,volume.meta(*container["position"]))
                if kind=="mist":
                    self.assertGreater(report["block_counts"].get("9",0),1000000)
                if kind=="fire":
                    self.assertGreater(report["block_counts"].get("11",0),0)

    def test_forest_all_routes_and_crown(self):self.check_site("forest")
    def test_cloud_stairs_and_three_plates(self):self.check_site("cloud")
    def test_sand_dry_cistern_and_supported_cover(self):self.check_site("sand")
    def test_mist_real_water_and_recovery(self):self.check_site("mist")
    def test_fire_contained_lava_and_grounded_gate(self):self.check_site("fire")

    def test_wrong_dimensions_refused_before_writes(self):
        with self.assertRaisesRegex(ValueError,"exactly"):
            build_geometry(BRIEF["arenas"][0],SimpleNamespace(dimensions=(499,250,500)),1)


if __name__=="__main__":unittest.main()
