package com.example.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.RedirectView;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeControllerTest {

    @Test
    void rootShouldOpenReactDashboardWorkbench() {
        HomeController controller = new HomeController();

        RedirectView view = controller.home();

        assertEquals("/react-dashboard/index.html", view.getUrl());
    }
}
