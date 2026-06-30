package com.example.demo.DTO;

import java.util.Map;


public class PromptRequest {

    private String template;
    private Map<String, String> variables;

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

}
