package me.hanju.branchdown;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import me.hanju.branchdown.config.TestcontainersConfig;

/** Base class for integration tests backed by a PostgreSQL Testcontainer. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
@Transactional
public abstract class IntegrationTestBase {
}
