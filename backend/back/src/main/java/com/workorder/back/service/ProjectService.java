package com.workorder.back.service;

import com.workorder.back.dto.ProjectDetailVO;
import com.workorder.back.dto.ProjectSaveRequest;
import com.workorder.back.dto.ProjectVO;

import java.util.List;

public interface ProjectService {

    List<ProjectVO> listPublished();

    ProjectDetailVO getPublishedDetail(Long id);

    List<ProjectVO> listAdmin();

    ProjectDetailVO getAdminDetail(Long id);

    Long create(ProjectSaveRequest request);

    void update(Long id, ProjectSaveRequest request);

    void delete(Long id);
}
