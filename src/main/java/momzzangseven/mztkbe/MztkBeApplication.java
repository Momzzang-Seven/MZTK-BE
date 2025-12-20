package momzzangseven.mztkbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Application entry point for the MZTK backend service. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MztkBeApplication {

  public static void main(String[] args) {
    SpringApplication.run(MztkBeApplication.class, args);
  }
}
