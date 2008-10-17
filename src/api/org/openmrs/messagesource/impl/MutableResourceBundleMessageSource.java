/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.messagesource.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.messagesource.MutableMessageSource;
import org.openmrs.messagesource.PresentationMessage;
import org.openmrs.util.LocaleUtility;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;

/**
 * ResourceBundleMessageSource extends ReloadableResourceBundleMessageSource to 
 * provide the additional features of a MutableMessageSource.  
 * 
 */
public class MutableResourceBundleMessageSource extends ReloadableResourceBundleMessageSource
	implements MutableMessageSource, ApplicationContextAware {
	
	private static final String PROPERTIES_FILE_COMMENT = "OpenMRS Application Messages";

	private static Log log = LogFactory.getLog(MutableResourceBundleMessageSource.class);
	private ApplicationContext applicationContext;
	
	/** Local reference to basenames used to search for properties files.
	 */
	private String[] basenames = new String[0];

	/** Cached list of available locales. */
	private Collection<Locale> locales;
	
	/**
	 * @see org.openmrs.message.MessageSourceService#getLocalesOfConceptNames()
	 */
	public Collection<Locale> getLocales() {
		if (locales == null) 
			locales = findLocales();
		
		return locales;
	}
	
	/**
	 * This method looks at the current property files and deduces what
	 * locales are available from those
	 * 
	 * @see #getLocales()
	 * @see #findPropertiesFiles()
	 */
	private Collection<Locale> findLocales() {
		Collection<Locale> foundLocales = new HashSet<Locale>();
		
		/* ABK: deprecated
		locales.add(Locale.US);
		locales.add(Locale.UK);
		locales.add(Locale.FRENCH);
		locales.add(new Locale("es")); // Spanish
		locales.add(new Locale("pt")); // Portugese
		 */

		for (File propertiesFile : findPropertiesFiles()) {
			String filename = propertiesFile.getName();
			
			Locale parsedLocale = parseLocaleFrom(filename);
						
			foundLocales.add(parsedLocale);
			
		}
		
		if (foundLocales.size() == 0) {
			log.warn("no locales found.");
		}
		return foundLocales;
	}

	/**
     * Utility method for deriving a locale from a filename, presumed
     * to have an embedded locale specification near the end.
     * 
     * For instance messages_it.properties
     * 
     * if the filename is messages.properties, the Locale is presumed to 
     * be the default set for Java
     * 
     * @param filename the name to parse
     * @return Locale derived from the given string
     */
    private Locale parseLocaleFrom(String filename) {
    	Locale parsedLocale = null;
    	
    	// trim off leading basename
		for (String basename : basenames)
		{
			File basefilename = new File(basename);
			basename = basefilename.getPath();
			
			int indexOfLastPart = basename.lastIndexOf(File.separatorChar) + 1;
			if (indexOfLastPart > 0) basename = basename.substring(indexOfLastPart);
			
			if (filename.startsWith(basename))
			{
				filename = filename.substring(basename.length());
			}
		}
		
		// trim off extension
		String localespec = filename.substring(0, filename.indexOf('.'));
		
		if (localespec.equals("")) {
			parsedLocale = Locale.getDefault();
		}
		else
		{
			localespec = localespec.substring(1); // trim off leading '_'
			parsedLocale = LocaleUtility.fromSpecification(localespec);
		}
		return parsedLocale;
    }

	/**
	 * Presumes to append the messages to a message.properties file which is
	 * already being monitored by the super
	 * ReloadableResourceBundleMessageSource.
	 * 
	 * This is a blind, trusting hack.
	 * 
	 * @see org.openmrs.message.MessageSourceService#publishProperties(java.util.Properties,
	 *      java.lang.String, java.lang.String, java.lang.String)
	 */
	public void publishProperties(Properties props, String locale,
	        String namespace, String name, String version) {

		String filePrefix = (namespace.length() > 0) ? (namespace + "_") : "";
		String propertiesPath = "/WEB-INF/" + filePrefix + "messages" + locale
		        + ".properties";

		OutputStream outStream = null;
		try {

			Resource propertiesResource = applicationContext
			        .getResource(propertiesPath);
			File propertiesFile = propertiesResource.getFile();

			if (!propertiesFile.exists())
				propertiesFile.createNewFile();

			outStream = new FileOutputStream(propertiesFile, true);

			// append the properties to the appropriate messages file
			props.store(outStream, namespace + ": " + name + " v" + version);

		} catch (IOException e) {
			log.error("Unable to load in locale: '" + locale
			        + "' properties for " + namespace + ": " + name, e);
		} finally {
			if (outStream != null) {
				try {
					outStream.close();
				} catch (IOException e) {
					log.warn("Couldn't close outStream", e);
				}
			}
		}

	}

	/**
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	public void setApplicationContext(ApplicationContext applicationContext)
	        throws BeansException {
		this.applicationContext = applicationContext;

	}

	/**
	 * Returns all available messages.
	 * 
     * @see org.openmrs.message.MessageSourceService#getPresentations()
     */
    public Collection<PresentationMessage> getPresentations() {
    	Collection<PresentationMessage> presentations = new Vector<PresentationMessage>();
    	
    	for (File propertiesFile : findPropertiesFiles())
    	{
    		Locale currentLocale = parseLocaleFrom(propertiesFile.getName());
    		Properties props = new Properties();
    		try {
	            props.load(new FileInputStream(propertiesFile));
	            for (Map.Entry<Object, Object> property : props.entrySet()) {
	            	presentations.add(new PresentationMessage(property.getKey().toString(), 
	            			currentLocale,
	            			property.getValue().toString(), 
	            			""));
	            }
            } catch (FileNotFoundException e) {
	            // TODO Auto-generated catch block
	            log.error("Error generated", e);
            } catch (IOException e) {
	            // TODO Auto-generated catch block
	            log.error("Error generated", e);
            }
    		
    	}
    	return presentations;
    }
    
    
    
    /**
     * Override to obtain a local reference to the basenames.
     * @see org.springframework.context.support.ReloadableResourceBundleMessageSource#setBasename(java.lang.String)
     */
    @Override
    public void setBasename(String basename) {
	    super.setBasename(basename);
	    this.basenames = new String[] {basename};
    }

	/**
     * Override to obtain a local reference to the basenames.
     * @see org.springframework.context.support.ReloadableResourceBundleMessageSource#setBasenames(java.lang.String[])
     */
    @Override
    public void setBasenames(String[] basenames) {
    	super.setBasenames(basenames);
    	this.basenames = basenames;
    }

	/**
     * @see org.openmrs.message.MutableMessageSource#addPresentation(org.openmrs.messagesource.PresentationMessage)
     */
    public void addPresentation(PresentationMessage message) {
	    File propertyFile = findPropertiesFileFor(message.getCode());
	    if (propertyFile != null)
	    {
	    	Properties props = new Properties();
	    	try {
	    		FileInputStream fis = new FileInputStream(propertyFile);
	            props.load(fis);
	            fis.close();
	            props.setProperty(message.getCode(), message.getMessage());
	            FileOutputStream fos = new FileOutputStream(propertyFile);
	            props.store(fos, "OpenMRS Application Messages");
	            fos.close();
            } catch (FileNotFoundException e) {
	            log.error("Error generated", e);
            } catch (IOException e) {
	            log.error("Error generated", e);
            }
	    }
    }
    

	/**
     * @see org.openmrs.message.MutableMessageSource#removePresentation(org.openmrs.messagesource.PresentationMessage)
     */
    public void removePresentation(PresentationMessage message) {
	    File propertyFile = findPropertiesFileFor(message.getCode());
	    if (propertyFile != null)
	    {
	    	Properties props = new Properties();
	    	try {
	    		FileInputStream fis = new FileInputStream(propertyFile);
	            props.load(fis);
	            fis.close();
	            props.remove(message.getCode());
	            FileOutputStream fos = new FileOutputStream(propertyFile);
	            props.store(fos, PROPERTIES_FILE_COMMENT);
	            fos.close();
            } catch (FileNotFoundException e) {
	            log.error("Error generated", e);
            } catch (IOException e) {
	            log.error("Error generated", e);
            }
	    }
    }
    
	/**
	 * Convenience method to scan the available properties files,
	 * looking for the one that has a definition for the given code.
     * 
     * @param code
     * @return the file which defines the code, or null if not found
     */
    private File findPropertiesFileFor(String code) {
    	Properties props = new Properties();
    	File foundFile = null;
    	
    	for (File propertiesFile : findPropertiesFiles())
    	{
    		props.clear();
    		try {
	            props.load(new FileInputStream(propertiesFile));
            } catch (FileNotFoundException e) {
	            log.error("Error generated", e);
            } catch (IOException e) {
	            log.error("Error generated", e);
            }
    		if (props.containsKey(code))
    		{
    			foundFile = propertiesFile;
    			break;
    		}
    	}
    	return foundFile;
    }

	/**
     * Searches the filesystem for message properties files. 
     * 
     * ABKTODO: consider caching this, rather than searching every time
     * 
     * @return collection of property file names
     */
    private Collection<File> findPropertiesFiles()
    {
    	Collection<File> propertiesFiles = new Vector<File>();

		try {
			for (String basename : basenames)
			{
				File basefilename = new File(basename);
				basename = basefilename.getPath();
				int nameIndex = basename.lastIndexOf(File.separatorChar) + 1;
				String basedir = (nameIndex > 0) ? basename.substring(0, nameIndex) : "";
				String namePrefix = basename.substring(nameIndex);
				Resource propertiesDir = applicationContext.getResource(basedir);
				boolean filesFound = false;
				for (File possibleFile : propertiesDir.getFile().listFiles())
				{
					if (possibleFile.getName().startsWith(namePrefix) &&
							possibleFile.getName().endsWith(".properties"))
					{
						propertiesFiles.add(possibleFile);
						filesFound = true;
					}
				}
				if (log.isDebugEnabled() && !filesFound) {
					log.debug("No messages for basename " + basename);
				}
			}
        } catch (IOException e) {
	        log.error("Error generated", e);
        }
        if (log.isWarnEnabled() && (propertiesFiles.size() == 0)) {
        	log.warn("No properties files found.");
        }
		return propertiesFiles;
    }

	/**
     * @see org.openmrs.message.MutableMessageSource#merge(org.openmrs.message.MutableMessageSource)
     */
    public void merge(MutableMessageSource fromSource, boolean overwrite) {
    	
    	// collect all existing properties
    	Collection<File> propertiesFiles = findPropertiesFiles();
    	Map<Locale, List<File>> localeToFilesMap = new HashMap<Locale, List<File>>();
    	Map<File, Properties> fileToPropertiesMap = new HashMap<File, Properties>();
    	
    	for (File propertiesFile : propertiesFiles) {
    		Properties props = new Properties();
    		Locale propsLocale = parseLocaleFrom(propertiesFile.getName());
            List<File> propList = localeToFilesMap.get(propsLocale);
            if (propList == null) {
            	propList = new ArrayList<File>();
            	localeToFilesMap.put(propsLocale, propList);
            }
            
    		try {
	            props.load(new FileInputStream(propertiesFile));
	            fileToPropertiesMap.put(propertiesFile, props);
            } catch (FileNotFoundException e) {
	            // TODO Auto-generated catch block
	            log.error("Error generated", e);
            } catch (IOException e) {
	            // TODO Auto-generated catch block
	            log.error("Error generated", e);
            }
    	}
    	
    	// merge in the new properties
    	for (PresentationMessage message : fromSource.getPresentations()) {
    		Locale messageLocale = message.getLocale();
    		
    		Properties existingPropSource = null;
    		
    		List<File> filelist = localeToFilesMap.get(messageLocale);
    		if (filelist != null) {
    			for (File propertiesFile : filelist) {
    				Properties props = fileToPropertiesMap.get(propertiesFile);
    				if (props.containsKey(message.getCode())) {
    					existingPropSource = props;
    					if (overwrite) {
    						props.put(message.getCode(), message.getMessage());
    					}
    					break;
    				}
    			}
    		} 

    		if (existingPropSource == null) {
    			// no properties files for this locale, create one
    			File newPropertiesFile = new File(basenames[0] + "_" + messageLocale.toString());
    			Properties newProperties = new Properties();
    			fileToPropertiesMap.put(newPropertiesFile, newProperties);
    			newProperties.put(message.getCode(), message.getMessage());
    			List<File> newFilelist = new ArrayList<File>();
    			newFilelist.add(newPropertiesFile);
    			localeToFilesMap.put(messageLocale, newFilelist);
    		}
    		
    		
    		message.getCode();
    	}
    }

}