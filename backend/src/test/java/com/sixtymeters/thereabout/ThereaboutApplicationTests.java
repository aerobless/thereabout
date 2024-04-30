package com.sixtymeters.thereabout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@ActiveProfiles("test")
@SpringBootTest(classes = ThereaboutApplication.class)
@ContextConfiguration(classes = ThereaboutApplication.class)
class ThereaboutApplicationTests {

    @Test
    void contextLoads() {
    }

}
