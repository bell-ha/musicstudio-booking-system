package com.musicstudio.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 모듈 의존 방향을 검사한다 (ADR 0006, 0011).
 *
 * <pre>
 * practice ──┐
 * academy ───┼──▶ organization ──▶ account ──▶ common
 * site ──────┘
 * billing ─▶ academy (수강 등록 이벤트를 받는다. academy는 billing을 모른다)
 * </pre>
 *
 * 아직 클래스가 없는 패키지가 있어서 allowEmptyShould(true)를 둔다.
 */
@AnalyzeClasses(packages = "com.musicstudio", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyTest {

    @ArchTest
    static final ArchRule 연습실은_학원관리에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.practice..")
            .should().dependOnClassesThat().resideInAPackage("com.musicstudio.academy..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 학원관리는_연습실에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.academy..")
            .should().dependOnClassesThat().resideInAPackage("com.musicstudio.practice..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 기관은_기능_모듈에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.organization..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.musicstudio.practice..", "com.musicstudio.academy..", "com.musicstudio.site..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 사이트는_연습실과_학원관리에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.site..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.musicstudio.practice..", "com.musicstudio.academy..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 다른_모듈은_수납에_의존하지_않는다 = noClasses()
            .that().resideInAnyPackage("com.musicstudio.organization..", "com.musicstudio.academy..",
                    "com.musicstudio.practice..", "com.musicstudio.site..", "com.musicstudio.account..",
                    "com.musicstudio.common..")
            .should().dependOnClassesThat().resideInAPackage("com.musicstudio.billing..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 수납은_연습실과_사이트에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.billing..")
            .should().dependOnClassesThat().resideInAnyPackage("com.musicstudio.practice..", "com.musicstudio.site..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 연습실과_학원관리는_사이트에_의존하지_않는다 = noClasses()
            .that().resideInAnyPackage("com.musicstudio.practice..", "com.musicstudio.academy..")
            .should().dependOnClassesThat().resideInAPackage("com.musicstudio.site..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 계정은_기관과_기능_모듈에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.account..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.musicstudio.organization..", "com.musicstudio.practice..", "com.musicstudio.academy..",
                    "com.musicstudio.site..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 공통은_어떤_모듈에도_의존하지_않는다 = noClasses()
            .that().resideInAPackage("com.musicstudio.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.musicstudio.account..", "com.musicstudio.organization..",
                    "com.musicstudio.practice..", "com.musicstudio.academy..", "com.musicstudio.site..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 도메인은_api_계층에_의존하지_않는다 = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..")
            .allowEmptyShould(true);
}
