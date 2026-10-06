package com.musicstudio.academy.domain;

import com.musicstudio.common.crypto.EncryptedStringConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 원생. 학원이 관리하는 기록이라 계정이 없어도 된다(어린 원생).
 * 학생 계정이 있으면 membershipId로 연결한다. 한 계정은 기관 안에서 원생 하나에만 연결된다.
 * 연락처는 숫자만(010xxxxxxxx) 암호화해서 저장한다. toString에 넣지 않는다.
 */
@Entity
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long organizationId;

    private Long membershipId;

    private String name;

    private Short birthYear;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_enc")
    private String phone;

    private String guardianName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "guardian_phone_enc")
    private String guardianPhone;

    private String memo;

    private boolean active = true;

    protected Student() {
    }

    public Student(Long organizationId) {
        this.organizationId = organizationId;
    }

    public void update(String name, Integer birthYear, String phone, String guardianName, String guardianPhone,
                       String memo, boolean active) {
        this.name = name;
        this.birthYear = birthYear == null ? null : birthYear.shortValue();
        this.phone = phone;
        this.guardianName = guardianName;
        this.guardianPhone = guardianPhone;
        this.memo = memo;
        this.active = active;
    }

    public void linkAccount(Long membershipId) {
        this.membershipId = membershipId;
    }

    public Long getId() {
        return id;
    }

    public Long getMembershipId() {
        return membershipId;
    }

    public String getName() {
        return name;
    }

    public Short getBirthYear() {
        return birthYear;
    }

    public String getPhone() {
        return phone;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public String getGuardianPhone() {
        return guardianPhone;
    }

    public String getMemo() {
        return memo;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public String toString() {
        return "Student[id=" + id + "]";
    }
}
