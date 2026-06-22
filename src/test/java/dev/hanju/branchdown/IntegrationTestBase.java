package dev.hanju.branchdown;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import dev.hanju.branchdown.config.TestcontainersConfig;

/** Base class for integration tests backed by a PostgreSQL Testcontainer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
@Transactional
public abstract class IntegrationTestBase {
}
