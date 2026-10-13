package com.np.schedule.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ErpBoard {

    public String notice;
    public String orgName;
    public String orgSites;
    public Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<String, List<Map<String, Object>>>();
    public List<Map<String, String>> skills = new java.util.ArrayList<Map<String, String>>();
    public List<Map<String, String>> posts = new java.util.ArrayList<Map<String, String>>();
}
