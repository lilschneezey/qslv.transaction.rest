package qslv.transaction.rest;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@SelectPackages( "qslv.transaction.rest.unit")
@IncludeClassNamePatterns("^(Unit.*|.+[.$]Unit.*)$")
class UnitSuiteTransactionTest {


}
