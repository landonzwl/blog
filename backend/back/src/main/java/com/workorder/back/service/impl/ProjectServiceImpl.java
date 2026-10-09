package com.workorder.back.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.back.common.BusinessException;
import com.workorder.back.common.ResultCode;
import com.workorder.back.dto.ProjectDetailVO;
import com.workorder.back.dto.ProjectSaveRequest;
import com.workorder.back.dto.ProjectVO;
import com.workorder.back.entity.Project;
import com.workorder.back.mapper.ProjectMapper;
import com.workorder.back.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectServiceImpl(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public List<ProjectVO> listPublished() {
        List<Project> list = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getStatus, Project.STATUS_PUBLISHED)
                        .orderByAsc(Project::getSortOrder)
                        .orderByDesc(Project::getId));
        return toVOList(list);
    }

    @Override
    public ProjectDetailVO getPublishedDetail(Long id) {
        Project project = require(id);
        if (!Integer.valueOf(Project.STATUS_PUBLISHED).equals(project.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toDetail(project);
    }

    @Override
    public List<ProjectVO> listAdmin() {
        List<Project> list = projectMapper.selectList(
                new LambdaQueryWrapper<Project>()
                        .orderByAsc(Project::getSortOrder)
                        .orderByDesc(Project::getId));
        return toVOList(list);
    }

    @Override
    public ProjectDetailVO getAdminDetail(Long id) {
        return toDetail(require(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectSaveRequest request) {
        Project project = new Project();
        apply(project, request);
        projectMapper.insert(project);
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProjectSaveRequest request) {
        Project project = require(id);
        apply(project, request);
        projectMapper.updateById(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        require(id);
        projectMapper.deleteById(id);
    }

    private Project require(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return project;
    }

    private void apply(Project project, ProjectSaveRequest request) {
        project.setTitle(request.getTitle().trim());
        project.setSummary(trimToNull(request.getSummary()));
        project.setContent(request.getContent());
        project.setCoverUrl(trimToNull(request.getCoverUrl()));
        project.setTag(trimToNull(request.getTag()));
        project.setDateLabel(trimToNull(request.getDateLabel()));
        project.setStatus(request.getStatus());
        project.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<ProjectVO> toVOList(List<Project> list) {
        List<ProjectVO> result = new ArrayList<ProjectVO>();
        if (list == null) {
            return result;
        }
        for (Project p : list) {
            result.add(toVO(p));
        }
        return result;
    }

    private ProjectVO toVO(Project p) {
        ProjectVO vo = new ProjectVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setSummary(p.getSummary());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setTag(p.getTag());
        vo.setDateLabel(p.getDateLabel());
        vo.setStatus(p.getStatus());
        vo.setSortOrder(p.getSortOrder());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }

    private ProjectDetailVO toDetail(Project p) {
        ProjectDetailVO vo = new ProjectDetailVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setSummary(p.getSummary());
        vo.setContent(p.getContent());
        vo.setCoverUrl(p.getCoverUrl());
        vo.setTag(p.getTag());
        vo.setDateLabel(p.getDateLabel());
        vo.setStatus(p.getStatus());
        vo.setSortOrder(p.getSortOrder());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }
}
