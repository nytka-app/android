# Changelog

## [0.24.0](https://github.com/nytka-app/android/compare/v0.23.0...v0.24.0) (2026-10-22)


### Features

* **pendant:** recorder model, stored with the pairing and used to filter the chooser ([#130](https://github.com/nytka-app/android/issues/130)) ([94d18e5](https://github.com/nytka-app/android/commit/94d18e50da42037667ce474d8c741df1307cb27e))

## [0.23.0](https://github.com/nytka-app/android/compare/v0.22.0...v0.23.0) (2026-10-21)


### Features

* **transcribe:** optional on-device draft transcription with sherpa-onnx ([#128](https://github.com/nytka-app/android/issues/128)) ([60f5354](https://github.com/nytka-app/android/commit/60f5354958c7e7819cc76daf473b38794da9f30b))

## [0.22.0](https://github.com/nytka-app/android/compare/v0.21.0...v0.22.0) (2026-10-20)


### Features

* **people:** show names the server applied itself, with one-tap Undo ([#126](https://github.com/nytka-app/android/issues/126)) ([d4965ef](https://github.com/nytka-app/android/commit/d4965ef58d603d72b14a83643560b543e29a2c3e))
* **review:** play each inbox card's clip and show the lines around it ([#125](https://github.com/nytka-app/android/issues/125)) ([8088082](https://github.com/nytka-app/android/commit/8088082d2bfba3078b661bad9f4840ebd4688a28))

## [0.21.0](https://github.com/nytka-app/android/compare/v0.20.0...v0.21.0) (2026-10-20)


### Features

* add tasks with a due date; promises on the person page ([#115](https://github.com/nytka-app/android/issues/115)) ([5f775c3](https://github.com/nytka-app/android/commit/5f775c3b5eff2f3f70bb07089091cb88212b7ed0))
* save a transcript line as a memory; Ask offers questions ([#113](https://github.com/nytka-app/android/issues/113)) ([62e106b](https://github.com/nytka-app/android/commit/62e106bf8fdf0b3b143a00b32ae4cf937314ef5c))


### Bug Fixes

* drop stored audio within 6 s of a mute, past the 5 s of clock skew left uncorrected ([#118](https://github.com/nytka-app/android/issues/118)) ([ca4780b](https://github.com/nytka-app/android/commit/ca4780bdb672b07ab04628139a2398036ffb3937))
* four screens say fixed server notices instead of raw messages ([#120](https://github.com/nytka-app/android/issues/120)) ([eaeef7f](https://github.com/nytka-app/android/commit/eaeef7fb8016f9fb65b1126a16cd14cd844a9e09))
* keep server text and tokens out of errors and toString (invariant 6) ([#116](https://github.com/nytka-app/android/issues/116)) ([f0a688a](https://github.com/nytka-app/android/commit/f0a688a27aa3f3df23a70e60d91dfb930681d7e6))
* one refused chunk no longer blocks the queue; parked chunks count toward the cap ([#117](https://github.com/nytka-app/android/issues/117)) ([866fd52](https://github.com/nytka-app/android/commit/866fd52457f58b62370d7d80026c2e38bf5c03c3))

## [0.20.0](https://github.com/nytka-app/android/compare/v0.19.0...v0.20.0) (2026-10-19)


### Features

* suggest known names where you name a voice and as you write ([#111](https://github.com/nytka-app/android/issues/111)) ([9154074](https://github.com/nytka-app/android/commit/91540748347c4f2c3c600d52022af6d89a7874bf))

## [0.19.0](https://github.com/nytka-app/android/compare/v0.18.0...v0.19.0) (2026-10-19)


### Features

* Device sections, Developer screens and notifications follow the design system ([#108](https://github.com/nytka-app/android/issues/108)) ([3813ec7](https://github.com/nytka-app/android/commit/3813ec720ed77005b63178028fbedeec30ccabea))
* search, tags, review inbox, voice cards, citations and person dialogs follow the design system ([#107](https://github.com/nytka-app/android/issues/107)) ([7609648](https://github.com/nytka-app/android/commit/7609648ef11a5a4878490508b4a6da8568e9192d))
* tasks, conversation list, memories, ask and status card follow the design system ([#109](https://github.com/nytka-app/android/issues/109)) ([bd23513](https://github.com/nytka-app/android/commit/bd23513fcc591552eca280be5f4898513beb5866))

## [0.18.0](https://github.com/nytka-app/android/compare/v0.17.0...v0.18.0) (2026-10-19)


### Features

* add ListState, Monogram and a checked Switch from the design system ([#102](https://github.com/nytka-app/android/issues/102)) ([094b189](https://github.com/nytka-app/android/commit/094b189c8a0b7ec951a035060f12c3321839b818))
* adopt the Nytka design system (linen and ink) ([#99](https://github.com/nytka-app/android/issues/99)) ([cf89f03](https://github.com/nytka-app/android/commit/cf89f03de06102c6ca437e9e9f1ae2319d5fa474))
* first run and voice enrollment follow the design system ([#103](https://github.com/nytka-app/android/issues/103)) ([359ab58](https://github.com/nytka-app/android/commit/359ab5845a67d8572a6855a1443060e4da6e99e0))
* group Device and Developer settings by place; add pendant signals and About recording ([#106](https://github.com/nytka-app/android/issues/106)) ([18738b2](https://github.com/nytka-app/android/commit/18738b2872dba0f1b7d70d87c658c8a69a6dacde))
* people rows, person header and memory items follow the design system ([#105](https://github.com/nytka-app/android/issues/105)) ([da8f5f1](https://github.com/nytka-app/android/commit/da8f5f118b294620604fde5d6bb6b1682035d010))
* set long text in Literata at 64 characters a line ([#101](https://github.com/nytka-app/android/issues/101)) ([79e87fa](https://github.com/nytka-app/android/commit/79e87fab161ac626e46fb7c1df3563480e461030))
* speaker marks, docked audio player and saffron suggestion card ([#104](https://github.com/nytka-app/android/issues/104)) ([b252543](https://github.com/nytka-app/android/commit/b252543eecfbde2ec6f19bdde06669969e428514))

## [0.17.0](https://github.com/nytka-app/android/compare/v0.16.0...v0.17.0) (2026-10-06)


### Features

* accept every suggestion for one name in the banner and the inbox ([#81](https://github.com/nytka-app/android/issues/81)) ([53debfd](https://github.com/nytka-app/android/commit/53debfdb373e4c43cfd0f7b6a746ad4ee841aa8a))
* media chip and Hide media filter in the conversation list (Speech kind A-S3) ([#87](https://github.com/nytka-app/android/issues/87)) ([91f57b5](https://github.com/nytka-app/android/commit/91f57b5e5c269bb39e4a18cab217674562e728e0))
* phone context ranges (Speech kind A-S2) ([#84](https://github.com/nytka-app/android/issues/84)) ([a5f9f76](https://github.com/nytka-app/android/commit/a5f9f7621a4e5289a5ca08977f139cc30df31b3c))
* speech items in the review inbox (Speech kind A-S4) ([#86](https://github.com/nytka-app/android/issues/86)) ([9793680](https://github.com/nytka-app/android/commit/979368073c21a9e7faf48270959bfca05cb9510c))
* speech kind chips and marks (Speech kind A-S1) ([#83](https://github.com/nytka-app/android/issues/83)) ([1a0beb3](https://github.com/nytka-app/android/commit/1a0beb3aab3c126df26b80140fe6ac7d1193aa7d))

## [0.16.0](https://github.com/nytka-app/android/compare/v0.15.0...v0.16.0) (2026-10-05)


### Features

* proposed tags in conversations, person pages and the review inbox (A-T5) ([#77](https://github.com/nytka-app/android/issues/77)) ([35e614f](https://github.com/nytka-app/android/commit/35e614f3ad0a44c0ba7f4d1ab66b4a85e1169c8e))
* roles in name suggestions and people not named yet (A-T6) ([#79](https://github.com/nytka-app/android/issues/79)) ([274c7cb](https://github.com/nytka-app/android/commit/274c7cb5ca3f433bc099d4ab88ee9e917d26631d))
* tag filter on Conversations and People (A-T4) ([#76](https://github.com/nytka-app/android/issues/76)) ([955c562](https://github.com/nytka-app/android/commit/955c56202e8324dac1ec330fc18e0c5db2e486e1))

## [0.15.0](https://github.com/nytka-app/android/compare/v0.14.0...v0.15.0) (2026-10-05)


### Features

* open the filtered list from a tag (A-T3) ([#74](https://github.com/nytka-app/android/issues/74)) ([7d969d8](https://github.com/nytka-app/android/commit/7d969d866478f5f5f1da7f7ded8c56b196211470))
* tag chips with add and remove (A-T2) ([#75](https://github.com/nytka-app/android/issues/75)) ([a261464](https://github.com/nytka-app/android/commit/a2614648e65fccabf07833029fa6d9b11b14a592))
* tags client and models (A-T1) ([#72](https://github.com/nytka-app/android/issues/72)) ([51693ed](https://github.com/nytka-app/android/commit/51693ed27df41ee86c48a6857a0fff4c57f281b5))

## [0.14.0](https://github.com/nytka-app/android/compare/v0.13.0...v0.14.0) (2026-10-05)


### Features

* meeting-brief notifications (A-P8) ([#64](https://github.com/nytka-app/android/issues/64)) ([492f8b3](https://github.com/nytka-app/android/commit/492f8b3ff7811cc69a8cf4c561a5ba1ed0fc4613))
* name suggestions in conversations and people in search (A-P6) ([#66](https://github.com/nytka-app/android/issues/66)) ([981cdce](https://github.com/nytka-app/android/commit/981cdce1b3ab7037e42fb042e448ad9d35c25e88))
* optional consent chime while capturing (A-P9) ([#62](https://github.com/nytka-app/android/issues/62)) ([ccff6dd](https://github.com/nytka-app/android/commit/ccff6ddb71e48cc297ffc8127cef183c3da59764))
* People API clients, models and settings (A-P1) ([#60](https://github.com/nytka-app/android/issues/60)) ([76ce842](https://github.com/nytka-app/android/commit/76ce842d24c51e8606a83dc6e675fd7355e3c839))
* People settings (A-P7) ([#68](https://github.com/nytka-app/android/issues/68)) ([4ebc538](https://github.com/nytka-app/android/commit/4ebc538de8b749b34ca48ca752217ee7e7136473))
* People tab and list (A-P2) ([#63](https://github.com/nytka-app/android/issues/63)) ([856f3b5](https://github.com/nytka-app/android/commit/856f3b56603c0140d27bee5f07cde60da4249c85))
* person page (A-P3) ([#65](https://github.com/nytka-app/android/issues/65)) ([a72d38f](https://github.com/nytka-app/android/commit/a72d38f68031379c454dfed2aba6eb43298be389))
* review inbox (A-P5) ([#69](https://github.com/nytka-app/android/issues/69)) ([6726204](https://github.com/nytka-app/android/commit/6726204220207ad963290413a3d19c8806f6f221))
* Who is this cards with clip playback (A-P4) ([#67](https://github.com/nytka-app/android/issues/67)) ([16920ba](https://github.com/nytka-app/android/commit/16920baacdb2d41a2efd407ec27d8843857e22e9))

## [0.13.0](https://github.com/nytka-app/android/compare/v0.12.0...v0.13.0) (2026-10-04)


### Features

* show the app in Ukrainian and move UI text to string resources ([#43](https://github.com/nytka-app/android/issues/43)) ([34df560](https://github.com/nytka-app/android/commit/34df560836728df25d658911de75a3b023a4c39f))


### Bug Fixes

* **pendant:** reconnect a setUp whose client stopped taking requests ([#56](https://github.com/nytka-app/android/issues/56)) ([91f3337](https://github.com/nytka-app/android/commit/91f3337986cea0fd02a76ebb17fbfeaf31b3df3e))

## [0.12.0](https://github.com/nytka-app/android/compare/v0.11.0...v0.12.0) (2026-10-04)


### Features

* enroll your voice from the pendant (voice A-E) ([#53](https://github.com/nytka-app/android/issues/53)) ([9ef7f7f](https://github.com/nytka-app/android/commit/9ef7f7f110b26c58d2473219169e55b21fef43bb))
* mark a transcript line as yours or not yours ([#51](https://github.com/nytka-app/android/issues/51)) ([04983df](https://github.com/nytka-app/android/commit/04983df465554ea8cfeb74481bccb3c2b91eb21e))

## [0.11.0](https://github.com/nytka-app/android/compare/v0.10.0...v0.11.0) (2026-10-04)


### Features

* tell the user when Omi has published newer pendant firmware ([#47](https://github.com/nytka-app/android/issues/47)) ([063c807](https://github.com/nytka-app/android/commit/063c8077df21faaa19f847591cc1ddaf7b6fb23d))

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
