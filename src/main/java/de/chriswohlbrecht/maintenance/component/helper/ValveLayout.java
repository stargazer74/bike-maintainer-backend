package de.chriswohlbrecht.maintenance.component.helper;

/** Engine layout of a valve clearance worksheet: 1–8 cylinders with 2–4 valves each. */
public record ValveLayout(int cylinders, int valvesPerCylinder) {
}
