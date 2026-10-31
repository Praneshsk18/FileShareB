package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:testdb",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		"b2.bucket-name=test-bucket",
		"b2.endpoint=https://s3.us-west-000.backblazeb2.com",
		"b2.region=us-west-000",
		"b2.key-id=testkey",
		"b2.application-key=testappkey",
		"jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
})
class FileSharingApplicationTests {

	@Test
	void contextLoads() {
	}

}
