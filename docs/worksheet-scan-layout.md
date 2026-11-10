# Scannable worksheets – layout contract (version 1)

Printed worksheets (Epic #111) can be photographed with the app after filling them in by hand (Epic #120). The app
reads the sheet **without recognising its layout**: everything it needs follows from the QR code and the fixed
positions described here. Source of truth: `WorksheetScanLayout`; the app has a Kotlin twin. Both are tested against
`src/test/resources/worksheet-scan-layout-v1.json` (a copy lives in the app's test resources).

## Coordinates

PDF points (1/72 inch) on A4 (595.28 × 841.89), origin **top left**, y downwards.

## On every page

| Element | Position |
|---|---|
| Corner markers | solid black squares, 18 × 18, top left corners at (20, 20), (557.28, 20), (20, 803.89), (557.28, 803.89) – their centres give the perspective transform |
| QR code | 64 × 64 at (481.28, 80), error correction M, quiet zone of white around it |

## QR code content

`BMWS;v=<layout version>;t=<template>;id=<vehicle id>[;c=<cylinders>;vpc=<valves per cylinder>];p=<page>/<pages>`

Examples: `BMWS;v=1;t=TIRES;id=7;p=1/1`, `BMWS;v=1;t=VALVE_CLEARANCE;id=42;c=8;vpc=4;p=2/3`.
Only the vehicle id – no names, plates or other personal data. `c`/`vpc` only for `VALVE_CLEARANCE`.

## Fields

- **Digits:** one box of 18 × 24 per digit, boxes directly next to each other; after the integer digits (if there are
  decimals) a gap of 8 holding the decimal separator of the owner's language. Integers are written right-aligned,
  decimals left-aligned; empty boxes count as nothing (integer) or zero (decimals).
- **Checkbox:** 16 × 16.
- Field ids map to the app's measurement drafts, e.g. `valves.<cylinder>.<INTAKE|EXHAUST>.<index>.measured|shimOld|shimNew`,
  `brakes.padFront`, `chain.cleaned`, `tires.dotRear`.
- Free-text findings (condition, brake fluid), the "ok" boxes, "engine cold"/"TDC checked" and the fill-in lines are
  printed but not part of the contract.

| Template | Fields (integer + decimal digits) |
|---|---|
| Valve clearance | per valve: measured 1+3, shim old 1+3, shim new 1+3 |
| Brakes | pad and disc thickness front/rear 2+2 |
| Chain | slack 2+1, wear length 3+1; checkboxes cleaned, lubricated |
| Tyres | pressure front/rear 1+2, tread 2+1, DOT 4 digits |

Valve clearance spreads over several pages: as many cylinder tables per page as fit (4 valves: 3, 3 valves: 3,
2 valves: 4); a cylinder is never split. The fill-in fields (date, mileage, …) follow the last table or get a page of
their own.

## Changing the layout

Any change of a position or size is a new layout version: raise `WorksheetScanLayout.VERSION`, regenerate the JSON
(`./mvnw test -Dtest=WorksheetScanLayoutTest -Dbm.writeScanLayout=true`, which writes the file of the new version),
copy it into the app and teach the app the new version – old printouts carry the old version and must stay readable.
