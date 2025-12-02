package com.vyshali.positionloader;/*
 * 12/02/2025 - 11:20 AM
 * @author Vyshali Prabananth Lal
 */

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.vyshali.positionloader")
public class ArchitectureTest {
    @ArchTest
    static final ArchRule controllers_should_not_access_repositories = noClasses().that().resideInAPackage("..controller..").should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule services_should_be_in_service_package = classes().that().haveSimpleNameEndingWith("Service").should().resideInAPackage("..service..");
}
