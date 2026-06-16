package com.amatrix.sicprojectis_backend.structured;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sicprojectis_backend.security.AuthenticatedUser;
import com.amatrix.sicprojectis_backend.structured.dao.NoticeRecordDao;
import com.amatrix.sicprojectis_backend.structured.dto.NoticeUpsertRequest;
import com.amatrix.sicprojectis_backend.structured.entity.NoticeRecord;

@Service
public class NoticeService {
    private final NoticeRecordDao noticeDao;
    public NoticeService(NoticeRecordDao noticeDao) { this.noticeDao = noticeDao; }

    public List<NoticeRecord> list(String moduleType) {
        return moduleType == null || moduleType.isBlank() ? noticeDao.selectAll() : noticeDao.selectByModuleType(moduleType.trim());
    }

    public NoticeRecord get(Long noticeId) {
        NoticeRecord record = noticeDao.selectById(noticeId);
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice not found");
        return record;
    }

    @Transactional
    public NoticeRecord create(AuthenticatedUser user, NoticeUpsertRequest request) {
        validate(request);
        LocalDateTime now = LocalDateTime.now();
        NoticeRecord record = map(request);
        record.setPublishUserId(user.userId());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        noticeDao.insert(record);
        return record;
    }

    @Transactional
    public NoticeRecord update(Long noticeId, AuthenticatedUser user, NoticeUpsertRequest request) {
        validate(request);
        NoticeRecord existing = get(noticeId);
        NoticeRecord record = map(request);
        record.setNoticeId(noticeId);
        record.setModuleInstanceId(existing.getModuleInstanceId());
        record.setStateRecordId(existing.getStateRecordId());
        record.setPublishUserId(user.userId());
        record.setCreatedAt(existing.getCreatedAt());
        record.setUpdatedAt(LocalDateTime.now());
        noticeDao.updateById(record);
        return get(noticeId);
    }

    private NoticeRecord map(NoticeUpsertRequest request) {
        NoticeRecord record = new NoticeRecord();
        record.setModuleType(request.moduleType()); record.setNoticeType(request.noticeType()); record.setNoticeTitle(request.noticeTitle());
        record.setNoticeNo(request.noticeNo()); record.setPublishUnit(request.publishUnit()); record.setPublishTime(request.publishTime());
        record.setNoticeScope(request.noticeScope()); record.setTargetDeptScope(request.targetDeptScope()); record.setTargetUserScope(request.targetUserScope());
        record.setProjectCategory(request.projectCategory()); record.setIsLimitedProject(request.isLimitedProject()); record.setLimitCount(request.limitCount());
        record.setStartTime(request.startTime()); record.setDeadlineTime(request.deadlineTime());
        record.setMaterialRequirementSummary(request.materialRequirementSummary()); record.setContentSummary(request.contentSummary()); record.setRemark(request.remark());
        return record;
    }

    private void validate(NoticeUpsertRequest request) {
        if (request == null || blank(request.moduleType()) || blank(request.noticeType()) || blank(request.noticeTitle()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module type, notice type and title are required");
        if (request.startTime() != null && request.deadlineTime() != null && request.deadlineTime().isBefore(request.startTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notice deadline cannot be before start time");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
