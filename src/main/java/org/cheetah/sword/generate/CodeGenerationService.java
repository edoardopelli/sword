package org.cheetah.sword.generate;

import java.nio.file.Path;

import org.cheetah.sword.generate.writers.ControllerWriter;
import org.cheetah.sword.generate.writers.DtoResourceMapperWriter;
import org.cheetah.sword.generate.writers.DtoWriter;
import org.cheetah.sword.generate.writers.EntityDtoMapperWriter;
import org.cheetah.sword.generate.writers.EntityWriter;
import org.cheetah.sword.generate.writers.RepositoryWriter;
import org.cheetah.sword.generate.writers.ResourceWriter;
import org.cheetah.sword.generate.writers.ServiceWriter;
import org.cheetah.sword.model.DbModel;
import org.cheetah.sword.model.TableModel;
import org.springframework.stereotype.Service;

@Service
public class CodeGenerationService {

    public void generateAll(Path outputDir, String basePackage, DbModel dbModel) {
        EntityWriter entityWriter = new EntityWriter();
        DtoWriter dtoWriter = new DtoWriter();
        ResourceWriter resourceWriter = new ResourceWriter();
        RepositoryWriter repositoryWriter = new RepositoryWriter();
        EntityDtoMapperWriter entityDtoMapperWriter = new EntityDtoMapperWriter();
        DtoResourceMapperWriter dtoResourceMapperWriter = new DtoResourceMapperWriter();
        ServiceWriter serviceWriter = new ServiceWriter();
        ControllerWriter controllerWriter = new ControllerWriter();

        for (TableModel t : dbModel.getTables()) {
            entityWriter.write(outputDir, basePackage, dbModel.getSchema(), t);
            dtoWriter.write(outputDir, basePackage, t);
            resourceWriter.write(outputDir, basePackage, t);
            repositoryWriter.write(outputDir, basePackage, t);
            entityDtoMapperWriter.write(outputDir, basePackage, t);
            dtoResourceMapperWriter.write(outputDir, basePackage, t);
            serviceWriter.write(outputDir, basePackage, t);
            controllerWriter.write(outputDir, basePackage, t);
        }
    }
}