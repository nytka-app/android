# Changelog

## [0.10.0](https://github.com/nytka-app/android/compare/v0.9.0...v0.10.0) (2026-09-30)


### Features

* pendant settings, LED brightness and microphone gain (NYTKA-44) ([#41](https://github.com/nytka-app/android/issues/41)) ([6938a54](https://github.com/nytka-app/android/commit/6938a54af9c5e76b21418762735d5a3b4cd74380))

## [0.9.0](https://github.com/nytka-app/android/compare/v0.8.0...v0.9.0) (2026-09-30)


### Features

* **ask:** the Ask tab answers a question with numbered sources ([#37](https://github.com/nytka-app/android/issues/37)) ([c2f7415](https://github.com/nytka-app/android/commit/c2f741537ee7d281cfce27810d95a2e35e3a115c))
* audio playback on the conversation screen (v0.8) ([#39](https://github.com/nytka-app/android/issues/39)) ([197f876](https://github.com/nytka-app/android/commit/197f87678a84ca6582db9fe483ac0a1f1f1bd7bc))
* bookmarks (v0.8 track A-B) ([#38](https://github.com/nytka-app/android/issues/38)) ([2b06d90](https://github.com/nytka-app/android/commit/2b06d909fc1cfce661c312426fe2ca255626f835))

## [0.8.0](https://github.com/nytka-app/android/compare/v0.7.0...v0.8.0) (2026-09-30)


### Features

* **device:** push the mute schedule to the server as mute.windows ([#35](https://github.com/nytka-app/android/issues/35)) ([80ea519](https://github.com/nytka-app/android/commit/80ea519b85112eca81901964afd0a0fcbdd9cd5d))

## [0.7.0](https://github.com/nytka-app/android/compare/v0.6.0...v0.7.0) (2026-09-30)


### Features

* People screen ([#31](https://github.com/nytka-app/android/issues/31)) ([f5eca4d](https://github.com/nytka-app/android/commit/f5eca4d732fca23f24ebf7282e650b99c580b755))
* weekly mute schedule that feeds the capture controller's mute decision ([#33](https://github.com/nytka-app/android/issues/33)) ([d24cd04](https://github.com/nytka-app/android/commit/d24cd0400ed3de56940c6ea2b8e030e48f6f58ef))


### Bug Fixes

* **sync:** do not send STOP after a quiet READ, read again from the committed position ([#32](https://github.com/nytka-app/android/issues/32)) ([983bc51](https://github.com/nytka-app/android/commit/983bc510d5e8fe5ad8cfa8cfd77acd02fbc18d62))

## [0.6.0](https://github.com/nytka-app/android/compare/v0.5.1...v0.6.0) (2026-09-29)


### Features

* **app:** show Me and named voices in the transcript, name a voice by tapping it ([#29](https://github.com/nytka-app/android/issues/29)) ([6190577](https://github.com/nytka-app/android/commit/6190577f580073534f409ef54c84b76c8278452d))

## [0.5.1](https://github.com/nytka-app/android/compare/v0.5.0...v0.5.1) (2026-09-29)


### Bug Fixes

* merge stored frames into full chunks regardless of commit batching ([#26](https://github.com/nytka-app/android/issues/26)) ([f591aed](https://github.com/nytka-app/android/commit/f591aed087a38557866e4bb29202da14b0fdddad))

## [0.5.0](https://github.com/nytka-app/android/compare/v0.4.0...v0.5.0) (2026-09-29)


### Features

* Android v0.2 UI (track D) ([#23](https://github.com/nytka-app/android/issues/23)) ([6146786](https://github.com/nytka-app/android/commit/6146786ad8ca3f396882ca0fcdfb464bfef31ff0))
* five-tab skeleton and API scaffolding for v0.2 and v0.4 (track S2) ([#18](https://github.com/nytka-app/android/issues/18)) ([d9e0d2f](https://github.com/nytka-app/android/commit/d9e0d2fcc26a67ba5553dd65d05285e9c4668394))
* Memories tab, search and developer Webhooks screen (v0.4 track I) ([#22](https://github.com/nytka-app/android/issues/22)) ([674036c](https://github.com/nytka-app/android/commit/674036c3d76c274abdaf3334af55afdc5a752992))
* offline sync flow in :app (v0.3 track 3) ([#24](https://github.com/nytka-app/android/issues/24)) ([5142aaf](https://github.com/nytka-app/android/commit/5142aafc2965224cb1d9b9c918c69d20ba5302ed))
* offline sync screens (v0.3 track 4) ([#25](https://github.com/nytka-app/android/issues/25)) ([2f2a5dd](https://github.com/nytka-app/android/commit/2f2a5dd61cd4ddebd374194ae232f6008ac7ea08))
* **pendant:** storage protocol, ring records and pendant clock (v0.3 track 1) ([#21](https://github.com/nytka-app/android/issues/21)) ([e2588cb](https://github.com/nytka-app/android/commit/e2588cb0839d4ff81b861c521204074cf6497257))
* v0.3 track 2, ring times, queue and sync position in :core ([#20](https://github.com/nytka-app/android/issues/20)) ([75458f3](https://github.com/nytka-app/android/commit/75458f340fd50adf12d76883d51c913977b1a15d))

## [0.4.0](https://github.com/nytka-app/android/compare/v0.3.1...v0.4.0) (2026-09-29)


### Features

* log what can explain an audio stall, and stop reconnecting on quiet audio ([#15](https://github.com/nytka-app/android/issues/15)) ([c5dda57](https://github.com/nytka-app/android/commit/c5dda5738c9d9dcaae40b367987eda3f3a7e2acd))


### Bug Fixes

* first-run problems from the README audit ([#16](https://github.com/nytka-app/android/issues/16)) ([20b6180](https://github.com/nytka-app/android/commit/20b6180e985b46bcc9c8bfa0bef0294af2c02c74))

## [0.3.1](https://github.com/nytka-app/android/compare/v0.3.0...v0.3.1) (2026-09-29)


### Bug Fixes

* **pendant:** back off the audio watchdog and log the negotiated MTU ([#12](https://github.com/nytka-app/android/issues/12)) ([fc1ac84](https://github.com/nytka-app/android/commit/fc1ac8446b613a49c2b328be861682831da8fae5))

## [0.3.0](https://github.com/nytka-app/android/compare/v0.2.0...v0.3.0) (2026-09-29)


### Features

* send the app's own log events to the server with the diagnostics ([#9](https://github.com/nytka-app/android/issues/9)) ([0f97c19](https://github.com/nytka-app/android/commit/0f97c1950e71ade5fc6c755855570fd73a0d71fa))


### Bug Fixes

* **pendant:** survive a dead Bluetooth stack in GATT calls ([#10](https://github.com/nytka-app/android/issues/10)) ([570ba10](https://github.com/nytka-app/android/commit/570ba104acd985bac58e381fa34781b096716b50))

## [0.2.0](https://github.com/nytka-app/android/compare/v0.1.1...v0.2.0) (2026-09-29)


### Features

* record link and upload diagnostics; export them or send them to your server ([#5](https://github.com/nytka-app/android/issues/5)) ([58a9ed4](https://github.com/nytka-app/android/commit/58a9ed4a22437803c9c09f2c4aab1491a7b46f7a))

## [0.1.1](https://github.com/nytka-app/android/compare/v0.1.0...v0.1.1) (2026-09-29)


### Bug Fixes

* **pendant:** recover audio after a Bluetooth disconnect ([#6](https://github.com/nytka-app/android/issues/6)) ([929f1c7](https://github.com/nytka-app/android/commit/929f1c785df9d5dcb729bbc6b192e06d4753115f))

## 0.1.0 (2026-09-29)


### Features

* Nytka for Android v0.1 ([#1](https://github.com/nytka-app/android/issues/1)) ([9437d3f](https://github.com/nytka-app/android/commit/9437d3f31f5a5b048de649a09072ad05d816edd9))


### Bug Fixes

* **release:** start at 0.1.0 ([#3](https://github.com/nytka-app/android/issues/3)) ([953cea9](https://github.com/nytka-app/android/commit/953cea9474f0267ca2a06057d073094097ca89a2))
