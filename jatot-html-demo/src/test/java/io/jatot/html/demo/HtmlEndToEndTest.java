package io.jatot.html.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@AutoConfigureMockMvc
class HtmlEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testShowUserPage() throws Exception {
        mockMvc.perform(get("/users/Mel"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<!DOCTYPE html>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>Mel</title>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<article class=\"user-card\">")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<h2>Mel</h2>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<p>mel@example.com</p>")));
    }

    @Test
    void testShowFragment() throws Exception {
        mockMvc.perform(get("/fragment"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string("<div>Hello from raw Html interface!</div>"));
    }
}
