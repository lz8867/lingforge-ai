package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class HomeController {

    @RequestMapping(value = {"/"}, method = RequestMethod.GET)
    public RedirectView home() {
        // 默认进入 React 大屏工作台，旧版静态页面仍可通过 /index.html 访问。
        return new RedirectView("/react-dashboard/index.html");
    }
}
