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
package org.openmrs.web.filter.initialization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.openmrs.api.context.Context;
import org.openmrs.scheduler.SchedulerConstants;
import org.openmrs.util.DatabaseUpdateException;
import org.openmrs.util.DatabaseUpdater;
import org.openmrs.util.InputRequiredException;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.Listener;
import org.openmrs.web.WebConstants;
import org.springframework.web.context.ContextLoader;

/**
 * This is the first filter that is processed. It is only active when starting OpenMRS for the very
 * first time. It will redirect all requests to the {@link WebConstants#SETUP_PAGE_URL} if the
 * {@link Listener} wasn't able to find any runtime properties
 */
public class InitializationFilter implements Filter {
	
	protected static final Log log = LogFactory.getLog(InitializationFilter.class);
	
	private static final String LIQUIBASE_SCHEMA_DATA = "liquibase-schema-only.xml";
	
	private static final String LIQUIBASE_CORE_DATA = "liquibase-core-data.xml";
	
	private static final String LIQUIBASE_DEMO_DATA = "liquibase-demo-data.xml";
	
	private static VelocityEngine velocityEngine = null;
	
	/**
	 * The velocity macro page to redirect to if an error occurs or on initial startup
	 */
	private final String DEFAULT_PAGE = "databasesetup.vm";
	
	/**
	 * Set by the {@link #init(FilterConfig)} method so that we have access to the current
	 * {@link ServletContext}
	 */
	private FilterConfig filterConfig = null;
	
	/**
	 * The model object that holds all the properties that the rendered templates use. All
	 * attributes on this object are made available to all templates via reflection in the
	 * {@link #renderTemplate(String, Map, Writer)} method.
	 */
	private InitializationWizardModel wizardModel = null;
	
	/**
	 * Variable set at the end of the wizard when spring is being restarted
	 */
	private boolean initializationComplete = false;
	
	/**
	 * The web.xml file sets this {@link InitializationFilter} to be the first filter for all
	 * requests.
	 * 
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
	                                                                                         ServletException {
		
		if (Listener.runtimePropertiesFound() || isInitializationComplete()) {
			chain.doFilter(request, response);
		} else {
			// we only get here if the Listener didn't find a runtime
			// properties files
			
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			HttpServletResponse httpResponse = (HttpServletResponse) response;
			
			if (!httpRequest.getServletPath().equals("/" + WebConstants.SETUP_PAGE_URL)) {
				// send the user to the setup page 
				httpResponse.sendRedirect("/" + WebConstants.WEBAPP_NAME + "/" + WebConstants.SETUP_PAGE_URL);
			} else {
				
				// clear the error message that was potentially there from
				// the last page
				wizardModel.errorMessage = "";
				
				if (httpRequest.getMethod().equals("GET")) {
					doGet(httpRequest, httpResponse);
				} else if (httpRequest.getMethod().equals("POST")) {
					doPost(httpRequest, httpResponse);
				}
			}
			
			// Don't continue down the filter chain otherwise Spring complains
			// that it hasn't been set up yet.
			// The jsp and servlet filter are also on this chain, so writing to
			// the response directly here is the only option 
		}
		
	}
	
	/**
	 * Convenience method to set up the velocity context properly
	 */
	private void initializeVelocity() {
		if (velocityEngine == null) {
			velocityEngine = new VelocityEngine();
			
			Properties props = new Properties();
			props.setProperty(RuntimeConstants.RUNTIME_LOG, "initial_wizard_vel.log");
			//			props.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
			//				"org.apache.velocity.runtime.log.CommonsLogLogChute" );
			//			props.setProperty(CommonsLogLogChute.LOGCHUTE_COMMONS_LOG_NAME, 
			//					"initial_wizard_velocity");
			
			// so the vm pages can import the header/footer
			props.setProperty(RuntimeConstants.RESOURCE_LOADER, "class");
			props.setProperty("class.resource.loader.description", "Velocity Classpath Resource Loader");
			props.setProperty("class.resource.loader.class",
			    "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
			
			try {
				velocityEngine.init(props);
			}
			catch (Exception e) {
				log.error("velocity init failed, because: " + e);
			}
		}
	}
	
	/**
	 * Called by {@link #doFilter(ServletRequest, ServletResponse, FilterChain)} on GET requests
	 * 
	 * @param httpRequest
	 * @param httpResponse
	 */
	private void doGet(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		
		Writer writer = httpResponse.getWriter();
		
		Map<String, Object> referenceMap = new HashMap<String, Object>();
		
		File runtimeProperties = getRuntimePropertiesFile();
		
		try {
			runtimeProperties.createNewFile();
			wizardModel.canCreate = true;
		}
		catch (IOException io) {
			wizardModel.cannotCreateErrorMessage = io.getMessage();
		}
		
		if (runtimeProperties.canWrite()) {
			wizardModel.canWrite = true;
		}
		
		wizardModel.runtimePropertiesPath = runtimeProperties.getAbsolutePath();
		
		// delete the file again after testing the create/write
		// so that if the user stops the webapp before finishing
		// this wizard, they can still get back into it
		runtimeProperties.delete();
		
		// do step one of the wizard
		renderTemplate(DEFAULT_PAGE, referenceMap, writer);
	}
	
	/**
	 * Called by {@link #doFilter(ServletRequest, ServletResponse, FilterChain)} on POST requests
	 * 
	 * @param httpRequest
	 * @param httpResponse
	 */
	private void doPost(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		
		String page = httpRequest.getParameter("page");
		Map<String, Object> referenceMap = new HashMap<String, Object>();
		Writer writer = httpResponse.getWriter();
		
		// step one
		if ("databasesetup.vm".equals(page)) {
			wizardModel.databaseConnection = httpRequest.getParameter("database_connection");
			
			// asked the user for their desired database name
			
			if ("yes".equals(httpRequest.getParameter("current_openmrs_database"))) {
				wizardModel.databaseName = httpRequest.getParameter("openmrs_current_database_name");
				wizardModel.hasCurrentOpenmrsDatabase = true;
				// TODO check to see if this is an active database
				
			} else {
				// mark this wizard as a "to create database" (done at the end)
				wizardModel.hasCurrentOpenmrsDatabase = false;
				
				wizardModel.createTables = true;
				
				wizardModel.databaseName = httpRequest.getParameter("openmrs_new_database_name");
				// TODO create database now to check if its possible?
				
				wizardModel.createDatabaseUsername = httpRequest.getParameter("create_database_username");
				wizardModel.createDatabasePassword = httpRequest.getParameter("create_database_password");
				// TODO if either username or pw is null, ask again
				
			}
			
			renderTemplate("databasetablesanduser.vm", referenceMap, writer);
		}
		// step two
		else if ("databasetablesanduser.vm".equals(page)) {
			
			if (wizardModel.hasCurrentOpenmrsDatabase)
				wizardModel.createTables = "yes".equals(httpRequest.getParameter("create_tables"));
			
			if ("yes".equals(httpRequest.getParameter("current_database_user"))) {
				wizardModel.currentDatabaseUsername = httpRequest.getParameter("current_database_username");
				wizardModel.currentDatabasePassword = httpRequest.getParameter("current_database_password");
				wizardModel.hasCurrentDatabaseUser = true;
			} else {
				wizardModel.hasCurrentDatabaseUser = false;
				wizardModel.createDatabaseUser = true;
				// asked for the root mysql username/password 
				wizardModel.createUserUsername = httpRequest.getParameter("create_user_username");
				wizardModel.createUserPassword = httpRequest.getParameter("create_user_password");
			}
			renderTemplate("otherruntimeproperties.vm", referenceMap, writer);
		}
		// step three
		else if ("otherruntimeproperties.vm".equals(page)) {
			wizardModel.moduleWebAdmin = "yes".equals(httpRequest.getParameter("module_web_admin"));
			wizardModel.autoUpdateDatabase = "yes".equals(httpRequest.getParameter("auto_update_database"));
			
			renderTemplate("adminusersetup.vm", referenceMap, writer);
		}
		// optional step four
		else if ("adminusersetup.vm".equals(page)) {
			
			wizardModel.adminUserPassword = httpRequest.getParameter("new_admin_password");
			String adminUserConfirm = httpRequest.getParameter("new_admin_password_confirm");
			
			// throw back to admin user if passwords don't match
			if (!wizardModel.adminUserPassword.equals(adminUserConfirm)) {
				wizardModel.errorMessage = "Admin passwords don't match";
				renderTemplate("adminusersetup.vm", referenceMap, writer);
				return;
			}
			
			renderTemplate("wizardcomplete.vm", referenceMap, writer);
		} else if ("wizardcomplete.vm".equals(page)) {
			
			Properties runtimeProperties = new Properties();
			String connectionUsername;
			String connectionPassword;
			
			if (!wizardModel.hasCurrentOpenmrsDatabase) {
				// connect via jdbc and create a database
				String sql = "create database ? default character set utf8";
				int result = executeStatement(wizardModel.createDatabaseUsername, wizardModel.createDatabasePassword, sql,
				    wizardModel.databaseName);
				// throw the user back to the main screen if this error occurs
				if (result < 0) {
					renderTemplate(DEFAULT_PAGE, null, writer);
					return;
				}
			}
			
			if (wizardModel.createDatabaseUser) {
				// TODO should we have a different user for each db created ?
				connectionUsername = "openmrs_user";
				connectionPassword = "";
				// generate random password from this subset of alphabet
				// intentionally left out these characters: ufs$()
				String chars = "acdeghijklmonpqrtvwxyzACDEGHIJKLMNOPQRTVWXYZ0123456789.|~@#^&";
				Random r = new Random();
				for (int x = 0; x < 12; x++) {
					connectionPassword += chars.charAt(r.nextInt(chars.length()));
				}
				
				// connect via jdbc with root user and create an openmrs user
				String sql = "drop user ?";
				int errMessageSize = wizardModel.errorMessage.length();
				executeStatement(wizardModel.createUserUsername, wizardModel.createUserPassword, sql, connectionUsername);
				// if an error is thrown, pull it back out of the error message
				if (errMessageSize != wizardModel.errorMessage.length())
					wizardModel.errorMessage = wizardModel.errorMessage.substring(0, errMessageSize);
				sql = "create user ? identified by '?'";
				executeStatement(wizardModel.createUserUsername, wizardModel.createUserPassword, sql, connectionUsername,
				    connectionPassword);
				
				// grant the roles
				sql = "GRANT ALL ON ?.* TO ?";
				executeStatement(wizardModel.createUserUsername, wizardModel.createUserPassword, sql,
				    wizardModel.databaseName, connectionUsername);
				
			} else {
				connectionUsername = wizardModel.currentDatabaseUsername;
				connectionPassword = wizardModel.currentDatabasePassword;
			}
			
			// save the properties for startup purposes
			String databaseConnectionFinalUrl = wizardModel.databaseConnection.replace("@DBNAME@", wizardModel.databaseName);
			runtimeProperties.put("connection.url", databaseConnectionFinalUrl);
			runtimeProperties.put("connection.username", connectionUsername);
			runtimeProperties.put("connection.password", connectionPassword);
			runtimeProperties.put("module.allow_web_admin", wizardModel.moduleWebAdmin.toString());
			runtimeProperties.put("auto_update_database", wizardModel.autoUpdateDatabase.toString());
			runtimeProperties.put(SchedulerConstants.SCHEDULER_USERNAME_PROPERTY, "admin");
			runtimeProperties.put(SchedulerConstants.SCHEDULER_PASSWORD_PROPERTY, wizardModel.adminUserPassword);
			
			Context.setRuntimeProperties(runtimeProperties);
			
			if (wizardModel.createTables) {
				// use liquibase to create core data + tables
				// TODO also give option of creating demo data
				try {
					DatabaseUpdater.executeChangelog(LIQUIBASE_SCHEMA_DATA, null);
					DatabaseUpdater.executeChangelog(LIQUIBASE_CORE_DATA, null);
					DatabaseUpdater.executeChangelog(LIQUIBASE_DEMO_DATA, null);
				}
				catch (Exception e) {
					wizardModel.errorMessage += "<br/>" + e.getMessage();
					wizardModel.errorMessage += "<br/>See the error log for more details"; // TODO internationalize this
					log.warn("Error while trying to create tables and demo data", e);
				}
			}
			
			// update the database to the latest version
			try {
				DatabaseUpdater.update();
			}
			catch (Exception e) {
				wizardModel.errorMessage += "<br/>" + e.getMessage();
				log.warn("Error while trying to update to the latest database version", e);
			}
			
			// redirect to setup page if we got an error
			if (wizardModel.errorMessage != null && !wizardModel.errorMessage.equals("")) {
				renderTemplate(DEFAULT_PAGE, null, writer);
				return;
			}
			
			// output properties to the openmrs runtime properties file
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(getRuntimePropertiesFile());
				runtimeProperties.store(fos, "Auto generated by OpenMRS initialization wizard");
			}
			finally {
				if (fos != null) {
					fos.close();
				}
			}
			
			// start spring
			// logic copied from org.springframework.web.context.ContextLoaderListener
			new ContextLoader().initWebApplicationContext(filterConfig.getServletContext());
			
			// start openmrs
			try {
				Context.startup(runtimeProperties);
			}
			catch (DatabaseUpdateException updateEx) {
				log.warn("Error while running the database update file", updateEx);
				wizardModel.errorMessage = "There was an error while running the database update file: "
				        + updateEx.getMessage();
				renderTemplate(DEFAULT_PAGE, null, writer);
				return;
			}
			catch (InputRequiredException inputRequiredEx) {
				// TODO display a page looping over the required input and ask the user for each.  
				// 		When done and the user and put in their say, call DatabaseUpdater.update(Map); 
				//		with the user's question/answer pairs
				log
				        .warn("Unable to continue because user input is required for the db updates, but I am not doing anything about that right now");
				wizardModel.errorMessage = "Unable to continue because user input is required for the db updates, but I am not doing anything about that right now";
				renderTemplate(DEFAULT_PAGE, null, writer);
				return;
			}
			
			// TODO catch errors here and drop the user back out to the setup screen
			
			// change the admin user password from "test" to what they input above
			if (wizardModel.createTables) {
				Context.authenticate("admin", "test");
				Context.getUserService().changePassword("test", wizardModel.adminUserPassword);
				Context.logout();
			}
			
			// load modules
			Listener.loadCoreModules(filterConfig.getServletContext());
			
			// web load modules
			Listener.performWebStartOfModules(filterConfig.getServletContext());
			
			// set this so that the wizard isn't run again on next page load
			initializationComplete = true;
			
			// TODO send user to confirmation page with results of wizard, location of file, etc instead?
			httpResponse.sendRedirect("/" + WebConstants.WEBAPP_NAME);
		}
		
	}
	
	/**
	 * Convenience method to load the runtime properties in the application data directory
	 * 
	 * @return
	 */
	private File getRuntimePropertiesFile() {
		String filename = WebConstants.WEBAPP_NAME + "-runtime.properties";
		
		File file = new File(OpenmrsUtil.getApplicationDataDirectory(), filename);
		
		log.debug("Using file: " + file.getAbsolutePath());
		
		return file;
	}
	
	/**
	 * All private attributes on this class are returned to the template via the velocity context
	 * and reflection
	 * 
	 * @param templateName
	 * @param referenceMap
	 * @param writer
	 */
	private void renderTemplate(String templateName, Map<String, Object> referenceMap, Writer writer) throws IOException {
		VelocityContext velocityContext = new VelocityContext();
		
		if (referenceMap != null) {
			for (Map.Entry<String, Object> entry : referenceMap.entrySet()) {
				velocityContext.put(entry.getKey(), entry.getValue());
			}
		}
		
		// put each of the private varibles into the template for convenience
		for (Field field : InitializationWizardModel.class.getDeclaredFields()) {
			try {
				velocityContext.put(field.getName(), field.get(wizardModel));
			}
			catch (IllegalArgumentException e) {
				log.error("Error generated while getting field value: " + field.getName(), e);
			}
			catch (IllegalAccessException e) {
				log.error("Error generated while getting field value: " + field.getName(), e);
			}
		}
		
		String fullTemplatePath = "org/openmrs/web/filter/initialization/" + templateName;
		InputStream templateInputStream = getClass().getClassLoader().getResourceAsStream(fullTemplatePath);
		if (templateInputStream == null)
			throw new IOException("Unable to find " + fullTemplatePath);
		
		try {
			velocityEngine.evaluate(velocityContext, writer, this.getClass().getName(), new InputStreamReader(
			        templateInputStream));
		}
		catch (Exception e) {
			throw new RuntimeException("Unable to process template: " + fullTemplatePath, e);
		}
	}
	
	/**
	 * @see javax.servlet.Filter#destroy()
	 */
	public void destroy() {
	}
	
	/**
	 * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
	 */
	public void init(FilterConfig filterConfig) throws ServletException {
		this.filterConfig = filterConfig;
		initializeVelocity();
		wizardModel = new InitializationWizardModel();
	}
	
	/**
	 * @param user username to connect with
	 * @param pw password to connect with
	 * @param sql String containing sql and question marks
	 * @param args the strings to fill into the question marks in the given sql
	 * @return result of executeUpdate or -1 for error
	 */
	private int executeStatement(String user, String pw, String sql, String... args) {
		
		Connection connection = null;
		try {
			// TODO how to get the driver for the other dbs...
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			
			String tempDatabaseConnection = wizardModel.databaseConnection.replace("@DBNAME@", ""); // make this dbname agnostic so we can create the db
			connection = DriverManager.getConnection(tempDatabaseConnection, user, pw);
			
			String replacedSql = sql;
			for (String arg : args) {
				arg = arg.replace(";", "&#094");
				replacedSql = replacedSql.replaceFirst("\\?", arg);
			}
			
			// do the create
			Statement statement = connection.createStatement();
			return statement.executeUpdate(replacedSql);
			
		}
		catch (SQLException sqlex) {
			// send user back to screen with an error message
			log.warn("error executing sql: " + sql, sqlex);
			wizardModel.errorMessage += "<br/>Error executing sql: " + sql + " - " + sqlex.getMessage();
		}
		catch (InstantiationException e) {
			log.error("Error generated", e);
		}
		catch (IllegalAccessException e) {
			log.error("Error generated", e);
		}
		catch (ClassNotFoundException e) {
			log.error("Error generated", e);
		}
		finally {
			try {
				connection.close();
			}
			catch (Throwable t) {
				log.warn("Error while closing connection", t);
			}
		}
		
		return -1;
	}
	
	/**
	 * Convenience variable to know if this wizard has completed successfully and that this wizard
	 * does not need to be executed again
	 * 
	 * @return
	 */
	private boolean isInitializationComplete() {
		return initializationComplete;
	}
}