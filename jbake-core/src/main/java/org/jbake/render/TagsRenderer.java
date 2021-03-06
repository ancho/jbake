package org.jbake.render;

import org.apache.commons.configuration.CompositeConfiguration;
import org.jbake.app.ContentStore;
import org.jbake.app.Renderer;
import org.jbake.app.configuration.JBakeConfiguration;
import org.jbake.app.configuration.JBakeConfigurationFactory;
import org.jbake.template.RenderingException;

import java.io.File;


public class TagsRenderer implements RenderingTool {

    @Override
    public int render(Renderer renderer, ContentStore db, JBakeConfiguration config) throws RenderingException {
        if (config.getRenderTags()) {
            try {
                //TODO: refactor this. the renderer has a reference to the configuration
                return renderer.renderTags(config.getTagPathName());
            } catch (Exception e) {
                throw new RenderingException(e);
            }
        } else {
            return 0;
        }
    }

    @Override
    public int render(Renderer renderer, ContentStore db, File destination, File templatesPath, CompositeConfiguration config) throws RenderingException {
        JBakeConfiguration configuration = new JBakeConfigurationFactory().createDefaultJbakeConfiguration(templatesPath.getParentFile(), config);
        return render(renderer, db, configuration);
    }

}
