package com.kreuz45.plugins;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.EnvironmentContributingAction;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.util.StringUtils;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 * @author Kohsuke Kawaguchi
 */
public class HelloWorldBuilder extends Builder {

    private final String name;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public HelloWorldBuilder(String name) {
        this.name = name;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }
    
    private ArrayList<String> readFromFile(File file) {
    	try {
    		ArrayList<String> lines = new ArrayList<String>();
			BufferedReader b = new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"));
			while(true) {
				String line = b.readLine();
				lines.add(line);
				if (line == null) break;
			}
			b.close();
			return lines;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        // This is where you 'build' the project.
        // Since this is a dummy, we just say 'hello world' and call that a build.
    	
    	String buildBaseDir = build.getArtifactsDir().getParent();
    	File changeLog = new File(buildBaseDir + "/changelog.xml");
    	ArrayList<String> changeLogLines = this.readFromFile(changeLog);
    	
//    	listener.getLogger().println("changelog.xml is: " + changeLogBody);
    	
    	// Parse
    	
    	ArrayList<String> commits = new ArrayList<String>();
    	StringBuffer buf = null;
    	String currentCommitter = null;
    	for (String line : changeLogLines) {
        	if (line != null) {
        		
        		// committer?
        		{
	        		String regex = "committer (.*?) <";
	            	Pattern p = Pattern.compile(regex);
		        	Matcher m = p.matcher(line);
		        	if (m.find()){
		        	  listener.getLogger().println(m.group(1));
		        	  currentCommitter = m.group(1);
		        	  if (buf != null) {
		        		  commits.add(buf.toString() + " - " + currentCommitter);
		        	  }
		        	  buf = new StringBuffer();
		        	}
        		}
        		
        		// commit msg?
        		{
        			if (line.startsWith("    ")) {
        				listener.getLogger().println(line);
        				buf.append(line.substring(4, line.length()));
        			}
        		}
        	}
    	}
    	if (buf != null) {
    		commits.add(buf.toString() + " - " + currentCommitter);
    	}
    	String modifiedLog = StringUtils.arrayToDelimitedString(commits.toArray(), "\n----\n");
    	
    	listener.getLogger().println("MSG:");
    	listener.getLogger().println(modifiedLog);
    	
        EnvAction envAction = new EnvAction();
        envAction.add("CHANGELOG", modifiedLog);
        build.addAction(envAction);
        
        // This also shows how you can consult the global configuration of the builder
        if (getDescriptor().getUseFrench())
            listener.getLogger().println("Bonjour, "+name+"!");
        else
            listener.getLogger().println("Hello, "+name+"!");
        return true;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean useFrench;

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Changelog to ENV";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            useFrench = formData.getBoolean("useFrench");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req,formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         *
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public boolean getUseFrench() {
            return useFrench;
        }
    }
    private static class EnvAction implements EnvironmentContributingAction {
        private transient Map<String,String> data = new HashMap<String,String>();

        private void add(String key, String value) {
            if (data==null) return;
            data.put(key, value);
        }

        public void buildEnvVars(AbstractBuild<?,?> build, EnvVars env) {
            if (data!=null) env.putAll(data);
        }

        public String getIconFileName() { return null; }
        public String getDisplayName() { return null; }
        public String getUrlName() { return null; }
    } 
}

