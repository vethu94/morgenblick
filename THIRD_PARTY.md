# Libraries and data sources

Morgenblick's own code is MIT licensed. This does not change licenses for third-party libraries, news text, images or broadcasts.

| Dependency | Version | License |
|---|---|---|
| biweekly | 0.6.8 | BSD-2-Clause |
| vinnie | 2.0.2 | MIT |
| Jackson Core, Databind and Annotations | 2.16.1 | Apache-2.0 |

The original license and notice files available in the dependency archives are included in `licenses/` and in the APK's Java resources. Dependency URLs and SHA-256 digests are fixed in `dependencies.json`.

Data sources:

- News RSS and images: [tagesschau.de](https://www.tagesschau.de/infoservices/rssfeeds). Original publishers and image credits are displayed. Content is fetched at runtime for the user's private dashboard; no news articles or photographs are bundled in this repository. Review the publisher's conditions before deploying for public or commercial use.
- Video: [SRF Tagesschau](https://www.srf.ch/play/tv/sendung/tagesschau?id=ff969c14-c5a7-44ab-ab72-14d4c9e427a9), using the official HTTPS video-podcast feed and its media URLs. SRF retains all broadcast rights. No media, DRM bypass or geo-restriction bypass is included.
- Weather: [Open-Meteo](https://open-meteo.com/), data attribution under CC BY 4.0; see its [terms](https://open-meteo.com/en/terms). The free endpoint is intended for eligible non-commercial usage.
- Swiss public transport: [transport.opendata.ch](https://transport.opendata.ch/docs.html). A community API, not an official SBB app or a complete disruption feed. Its attribution and upstream conditions apply.
- Calendar: a user-provided secret Google Calendar iCal URL. No example live secret is included.

This independent project is not affiliated with Philips, Google, SRF, ARD, SBB or the data providers. Names identify compatibility and sources; no provider logos are bundled.
