package io.jatot.html.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import io.jatot.html.Component;
import io.jatot.html.Html;

@SpringBootApplication
@Controller
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @GetMapping("/users/{name}")
    public Component showUser(@PathVariable("name") String name) {
        User user = new User(name, name.toLowerCase() + "@example.com");
        return new UserPage(user);
    }

    @GetMapping("/fragment")
    public Html showFragment() {
        return writer -> {
            writer.literal("<div>Hello from raw Html interface!</div>");
        };
    }
}
