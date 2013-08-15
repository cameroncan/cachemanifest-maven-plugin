package co.cantina.maven.plugin.cachemanifest;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Generates a Cache Manifest
 * 
 * as per http://www.whatwg.org/specs/web-apps/current-work/multipage/offline.html#manifests
 * 
 * @goal generate-manifest
 */
public class CacheManifestMojo extends AbstractMojo {

	/**
	 * The manifest file to generate
	 * 
	 * @required
	 * @parameter
	 */
	private File outputManifestFile;

	/**
	 * The directory to iterate over.
	 *
	 * @required
	 * @parameter
	 */
	private File inputDirectory;

	/**
	 * An optional version number; used to indicate to clients (via a 
	 * difference in file) that they should refresh all resources
	 * 
	 * @parameter
	 */
	private String manifestVersion;

	/**
	 * A list of inclusion filters for the manifest.  If none are provided,
	 * will use all files.
	 * 
	 * @parameter
	 */
	private Set<String> includes = new HashSet<String>();

	/**
	 * A list of exclusion filters for the manifest.
	 * 
	 * @parameter
	 */
	private Set<String> excludes = new HashSet<String>();
	
	/**
	 * Additional Entries which are not Files
	 * @parameter
	 */
	private Set<String> additionals=new HashSet<String>();
	
	/**
	 * A list of resources that should be prepended by the NETWORK: token,
	 * implying that these resources should always be served over the 
	 * network (never cached).
	 * 
	 * @parameter
	 */
	private List<String> networkResources = new ArrayList<String>();

	/**
	 * An optional fallback expression
	 * 
	 * @parameter
	 */
	private String fallback;

	/* (non-Javadoc)
	 * Generates the manifest.
	 * 
	 * @see org.apache.maven.plugin.AbstractMojo#execute()
	 */
	public void execute() throws MojoExecutionException {
		
		if(includes.isEmpty()) {
			includes.add("**/*.*"); // if no include is specified, include everything
		}
		
		SourceInclusionScanner scanner = new SimpleSourceInclusionScanner(includes, excludes);

		// Note: we must declare a dummy "source mapping", or the Plexus SimpleSourceInclusionScanner won't work
		// (as per http://maven.apache.org/plugins/maven-clover-plugin/2.4/xref/org/apache/maven/plugin/clover/CloverInstrumentInternalMojo.html )
		scanner.addSourceMapping( new SuffixMapping( "dummy", "dummy" ) );

		try {
			outputManifestFile.getParentFile().mkdirs();
			outputManifestFile.createNewFile(); // create it if it doesn't yet exist
		} catch (IOException e) {
			getLog().error("IOException creating manifest file: " + outputManifestFile.toString(), e);
			return;
		}
		
		try {
			// the manifest looks much nicer sorted - sort the set
			SortedSet<File> includedFiles = new TreeSet<File>((Set<File>)scanner.getIncludedSources(inputDirectory, null));
			
			Writer w = new BufferedWriter(new FileWriter(outputManifestFile));

			// build the header
			w.write("CACHE MANIFEST\n\n");
			w.write("#\n");
			w.write("# Generated by co.cantina.maven/cachemanifest-maven-plugin\n");
			if(manifestVersion != null && !manifestVersion.equals("")) {
				w.write("# version: ");
				w.write(manifestVersion);
				w.write("\n");
			}
			w.write("#\n");

			// build the CACHE: section
			if(includedFiles.isEmpty()) {
				w.write("# WARNING: No files matched provided include/exclude patterns\n");
				getLog().warn("No files matched provided include/exclude patterns");
			} else {
				// NOTE: the CACHE: header is only required if it comes AFTER a different section.
				// if it is present in the first section, it actually breaks the caching functionality.
				//w.write("\nCACHE:\n");
			}

			// paths should be relative - to do this, we'll strip chars off the front of each file's absolute path
			String fileStripPrefix = inputDirectory.toString();
			if(!fileStripPrefix.endsWith("/")) {
				fileStripPrefix += "/"; // ensure it ends with a slash
			}

			for(File f : includedFiles) {
				String relativeFilePath = f.toString().substring(fileStripPrefix.length());
				w.write(relativeFilePath);
				w.write("\n");
			}
			if(additionals.size()>0){
				w.write("# Additional Entries\n");
			}
			for(String addEntry: additionals){
				w.write(addEntry);
				w.write("\n");				
			}
			
			// optionally, build the NETWORK: section
			if(networkResources != null) {
				w.write("\nNETWORK:\n");
				for(String networkResource : networkResources) {
					w.write(networkResource);
					w.write("\n");
				}
			}
			
			// optionally, build the FALLBACK: section
			if(fallback != null && !fallback.equals("")) {
				w.write("\nFALLBACK:\n");
				w.write(fallback);
				w.write("\n");
			}
			
			w.close();
			
		} catch (InclusionScanException ex) {
			getLog().error(ex);
		} catch (IOException ex) {
			getLog().error(ex);
		}
	}
}