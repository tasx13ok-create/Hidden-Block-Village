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
                "routes":{k:{"failures":v["failures"][:3],"width_failures":v["width_failures"][:3]}
                          for k,v in report["routes"].items() if v["status"]!="PASS"},
                "stadium":{k:v for k,v in report.get("stadium_geometry",{}).items() if k!="architecture_by_y"},
                "layer_checks":{k:v for k,v in report.get("layer_checks",{}).get("layers",{}).items() if v["status"]!="PASS"}},indent=2))
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
                for source in geometry["light_sources"]:
                    self.assertEqual(169,volume.block(*source))
                lighting=geometry["route_light_path_design"]
                self.assertGreaterEqual(lighting["conservative_minimum"],8)
                self.assertEqual(255,lighting["stair_opacity"])
                self.assertEqual("NOT_RUN",lighting["native_block_light_acceptance"])
                self.assertGreater(lighting["added_exposed_fixtures"],0)
                for fixture in geometry["route_light_fixtures"]:
                    self.assertEqual(169,volume.block(*fixture["position"]))
                    self.assertEqual(0,volume.meta(*fixture["position"]))
                    self.assertNotIn(volume.block(*fixture["attached_to"]),(0,8,9,10,11,18))
                    route=fixture["air_path"]
                    self.assertEqual(fixture["served_at"],route[0])
                    self.assertEqual(fixture["position"],route[-1])
                    self.assertLessEqual(len(route)-1,7)
                    for a,b in zip(route,route[1:]):
                        self.assertEqual(1,sum(abs(a[i]-b[i]) for i in range(3)))
                    for cell in route[:-1]:
                        self.assertIn(volume.block(*cell),(0,20,95,102,160,169))
                if kind=="mist":
                    self.assertGreater(report["block_counts"].get("9",0),1000000)
                    wet=geometry["wet_routes"]["underwater_passage"]
                    self.assertEqual(3,wet["width"])
                    self.assertEqual(133,len(wet["points"]))
                    for z in range(179,312):
                        for x in range(154,157):
                            self.assertEqual(169,volume.block(x,46,z))
                            self.assertEqual(98,volume.block(x,45,z))
                    for x,y,z in wet["points"]:
                        for dx in (-1,0,1):
                            for dy in range(4):self.assertEqual(9,volume.block(x+dx,y+dy,z))
                    for z in (249,250,251):
                        self.assertEqual(98,volume.block(155,47,z))
                    prison=geometry["water_prison"]
                    self.assertEqual([278,63,208],prison["min"])
                    self.assertEqual([292,64,222],prison["max"])
                    for y in (63,64):
                        for x in range(277,294):
                            for z in (207,223):self.assertNotIn(volume.block(x,y,z),(0,8,9,10,11))
                        for z in range(207,224):
                            for x in (277,293):self.assertNotIn(volume.block(x,y,z),(0,8,9,10,11))
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
