package com.workorder.back.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workorder.back.entity.Project;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {
}
