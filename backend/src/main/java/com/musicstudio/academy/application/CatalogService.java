package com.musicstudio.academy.application;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musicstudio.academy.domain.Product;
import com.musicstudio.academy.domain.ProductKind;
import com.musicstudio.academy.domain.ProductRepository;
import com.musicstudio.academy.domain.Subject;
import com.musicstudio.academy.domain.SubjectRepository;
import com.musicstudio.common.error.ApiException;

/** 과목과 수업 상품 (UC-40). */
@Service
public class CatalogService {

    private final SubjectRepository subjects;
    private final ProductRepository products;

    CatalogService(SubjectRepository subjects, ProductRepository products) {
        this.subjects = subjects;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Catalog catalog(long orgId) {
        return new Catalog(subjects.findByOrganizationIdOrderByNameAsc(orgId),
                products.findByOrganizationIdOrderByNameAsc(orgId));
    }

    @Transactional
    public Subject createSubject(long orgId, String name) {
        try {
            return subjects.saveAndFlush(new Subject(orgId, name.trim()));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("SUBJECT_NAME_TAKEN", "같은 이름의 과목이 있습니다");
        }
    }

    @Transactional
    public Product createProduct(long orgId, long subjectId, String name, ProductKind kind, Integer sessionCount,
                                 Integer periodMonths, int lessonMinutes, long price) {
        subjects.findByIdAndOrganizationId(subjectId, orgId).orElseThrow(() -> ApiException.notFound("과목을 찾을 수 없습니다"));
        boolean valid = kind == ProductKind.COUNT
                ? sessionCount != null && sessionCount > 0 && periodMonths == null
                : periodMonths != null && periodMonths > 0 && sessionCount == null;
        if (!valid) {
            throw ApiException.invalid("INVALID_PRODUCT", "횟수권은 횟수만, 기간권은 개월 수만 정해 주세요");
        }
        return products.save(new Product(orgId, subjectId, name.trim(), kind, sessionCount, periodMonths,
                lessonMinutes, price));
    }

    /** 가격을 바꿔도 이미 등록된 수강의 금액은 그대로다. */
    @Transactional
    public Product updateProduct(long orgId, long productId, String name, Long price) {
        Product product = products.findByIdAndOrganizationId(productId, orgId)
                .orElseThrow(() -> ApiException.notFound("상품을 찾을 수 없습니다"));
        product.update(name == null ? null : name.trim(), price);
        return product;
    }

    public record Catalog(List<Subject> subjects, List<Product> products) {
    }
}
