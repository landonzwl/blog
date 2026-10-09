package com.workorder.back.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.back.common.BusinessException;
import com.workorder.back.common.ResultCode;
import com.workorder.back.dto.SiteContentItemVO;
import com.workorder.back.dto.SiteContentUpdateItem;
import com.workorder.back.entity.SiteContent;
import com.workorder.back.mapper.SiteContentMapper;
import com.workorder.back.service.SiteContentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SiteContentServiceImpl implements SiteContentService {

    private final SiteContentMapper siteContentMapper;

    public SiteContentServiceImpl(SiteContentMapper siteContentMapper) {
        this.siteContentMapper = siteContentMapper;
    }

    @Override
    public Map<String, String> mapPublic() {
        List<SiteContent> list = siteContentMapper.selectList(
                new LambdaQueryWrapper<SiteContent>()
                        .last("ORDER BY FIELD(group_name,'nav','hero','about','projects','articles','contact','site'), sort_order ASC, id ASC"));
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (SiteContent item : list) {
            map.put(item.getContentKey(), item.getContentValue());
        }
        return map;
    }

    @Override
    public List<SiteContentItemVO> listAdmin() {
        // 与首页模块顺序一致：导航 → Hero → 关于 → 作品 → 文章 → 联系 → 页脚
        List<SiteContent> list = siteContentMapper.selectList(
                new LambdaQueryWrapper<SiteContent>()
                        .last("ORDER BY FIELD(group_name,'nav','hero','about','projects','articles','contact','site'), sort_order ASC, id ASC"));
        List<SiteContentItemVO> result = new ArrayList<SiteContentItemVO>();
        for (SiteContent item : list) {
            result.add(toVO(item));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(List<SiteContentUpdateItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "更新列表不能为空");
        }
        for (SiteContentUpdateItem item : items) {
            if (item == null || !StringUtils.hasText(item.getContentKey())) {
                continue;
            }
            SiteContent existing = siteContentMapper.selectOne(
                    new LambdaQueryWrapper<SiteContent>()
                            .eq(SiteContent::getContentKey, item.getContentKey().trim()));
            if (existing == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "文案键不存在: " + item.getContentKey());
            }
            existing.setContentValue(item.getContentValue() == null ? "" : item.getContentValue());
            siteContentMapper.updateById(existing);
        }
    }

    private SiteContentItemVO toVO(SiteContent item) {
        SiteContentItemVO vo = new SiteContentItemVO();
        vo.setId(item.getId());
        vo.setContentKey(item.getContentKey());
        vo.setContentValue(item.getContentValue());
        vo.setLabel(item.getLabel());
        vo.setGroupName(item.getGroupName());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }
}
