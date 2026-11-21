package com.careerq.dataobject;

import java.time.LocalDateTime;

/**
 * HR 资料（hr_profiles）：公司、行业、职位与认证状态。
 */
public class HrProfileDO {

    private Long userId;
    private String companyDisplay;
    private String industry;
    private String position;
    private String bio;
    private String specialtiesJson;
    private String verifyStatus = "pending";
    private String verifyMaterialRef;
    private LocalDateTime verifiedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCompanyDisplay() {
        return companyDisplay;
    }

    public void setCompanyDisplay(String companyDisplay) {
        this.companyDisplay = companyDisplay;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getVerifyStatus() {
        return verifyStatus;
    }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getSpecialtiesJson() { return specialtiesJson; }
    public void setSpecialtiesJson(String specialtiesJson) { this.specialtiesJson = specialtiesJson; }

    public void setVerifyStatus(String verifyStatus) {
        this.verifyStatus = verifyStatus;
    }

    public String getVerifyMaterialRef() {
        return verifyMaterialRef;
    }

    public void setVerifyMaterialRef(String verifyMaterialRef) {
        this.verifyMaterialRef = verifyMaterialRef;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
