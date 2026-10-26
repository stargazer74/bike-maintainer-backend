package de.chriswohlbrecht.maintenance.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/** Features tagged {@code @household}, against an application context started with {@code bm.mode=household}. */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME,
        value = "de.chriswohlbrecht.maintenance.cucumber.steps,de.chriswohlbrecht.maintenance.cucumber.household")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@household")
public class HouseholdCucumberTest {
}
