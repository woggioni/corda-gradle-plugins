package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class AnnotatedFieldTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "annotated-field").build();
    }

    @Test
    public void testAnnotatedField() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "  @A",
                "  @B",
                "  @C",
                "  public static final String ANNOTATED_FIELD = \"<string-value>\"");
    }
}
