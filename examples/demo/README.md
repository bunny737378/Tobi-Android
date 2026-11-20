# Tobi Demo Connector

Install this connector by opening Tobi, selecting **Connectors**, and pasting the raw URL of `connector.json` into the install field.

The connector exposes one public open movie and an HLS test stream. It exists only to validate connector installation, browsing, details, and playback.

A production connector should replace the static functions in `connector.js` with source-specific calls through `Bridge.http.send` and return MMCP envelopes with the same shape.
