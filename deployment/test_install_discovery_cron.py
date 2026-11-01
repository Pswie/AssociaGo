"""Verify discovery scheduling without contacting any account."""

import copy
import unittest

import install_discovery_cron as discovery
import install_portal_cron as cron
from test_install_portal_cron import Provider


class DiscoveryCronTests(unittest.TestCase):
    def test_create_is_idempotent_and_keeps_the_existing_maintenance_job(self):
        provider = Provider()
        provider.jobs[10] = {
            "jobId": 10,
            **cron.specification(
                "https://portal.example.org", "synthetic-maintenance-" + "x" * 32
            )["job"],
        }
        before = copy.deepcopy(provider.jobs[10])
        spec = discovery.specification("synthetic-discovery-" + "x" * 32)
        self.assertEqual(cron.apply(provider, spec), {"action": "created", "jobId": 11})
        self.assertEqual(
            cron.apply(provider, spec), {"action": "unchanged", "jobId": 11}
        )
        self.assertEqual(provider.jobs[10], before)
        self.assertEqual(provider.writes, [("PUT", 11)])
        job = provider.jobs[11]
        self.assertEqual(job["url"], discovery.ORIGIN + "/v1/check")
        self.assertEqual(job["requestMethod"], 1)
        self.assertEqual(job["schedule"]["minutes"], list(range(0, 60, 5)))
        self.assertFalse(job["saveResponses"])
        self.assertFalse(job["redirectSuccess"])
        self.assertEqual(job["extendedData"]["body"], "")

    def test_incomplete_listing_stops_before_any_write(self):
        provider = Provider()
        provider.incomplete = True
        with self.assertRaises(cron.CronError):
            cron.apply(provider, discovery.specification("synthetic-" + "x" * 40))
        self.assertFalse(provider.writes)
