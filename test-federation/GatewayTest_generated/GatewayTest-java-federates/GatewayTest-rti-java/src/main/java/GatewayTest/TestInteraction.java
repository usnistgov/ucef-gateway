
package GatewayTest;

import java.util.HashSet;
import java.util.Set;

import hla.rti.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cpswt.utils.CpswtUtils;


import org.cpswt.hla.*;

/**
* The TestInteraction class implements the TestInteraction interaction in the
* GatewayTest simulation.
*/
public class TestInteraction extends C2WInteractionRoot {

	private static final Logger logger = LogManager.getLogger(TestInteraction.class);

	/**
	* Default constructor -- creates an instance of the TestInteraction interaction
	* class with default parameter values.
	*/
	public TestInteraction() { }

	
	
	private static int _booleanValue_handle;
	private static int _doubleValue_handle;
	private static int _intValue_handle;
	private static int _stringValue_handle;
	
	
	/**
	* Returns the handle (RTI assigned) of the "booleanValue" parameter of
	* its containing interaction class.
	*
	* @return the handle (RTI assigned) of the "booleanValue" parameter
	*/
	public static int get_booleanValue_handle() { return _booleanValue_handle; }
	
	/**
	* Returns the handle (RTI assigned) of the "doubleValue" parameter of
	* its containing interaction class.
	*
	* @return the handle (RTI assigned) of the "doubleValue" parameter
	*/
	public static int get_doubleValue_handle() { return _doubleValue_handle; }
	
	/**
	* Returns the handle (RTI assigned) of the "intValue" parameter of
	* its containing interaction class.
	*
	* @return the handle (RTI assigned) of the "intValue" parameter
	*/
	public static int get_intValue_handle() { return _intValue_handle; }
	
	/**
	* Returns the handle (RTI assigned) of the "stringValue" parameter of
	* its containing interaction class.
	*
	* @return the handle (RTI assigned) of the "stringValue" parameter
	*/
	public static int get_stringValue_handle() { return _stringValue_handle; }
	
	
	
	private static boolean _isInitialized = false;

	private static int _handle;

	/**
	* Returns the handle (RTI assigned) of the TestInteraction interaction class.
	* Note: As this is a static method, it is NOT polymorphic, and so, if called on
	* a reference will return the handle of the class pertaining to the reference,\
	* rather than the handle of the class for the instance referred to by the reference.
	* For the polymorphic version of this method, use {@link #getClassHandle()}.
	*/
	public static int get_handle() { return _handle; }

	/**
	* Returns the fully-qualified (dot-delimited) name of the TestInteraction
	* interaction class.
	* Note: As this is a static method, it is NOT polymorphic, and so, if called on
	* a reference will return the name of the class pertaining to the reference,\
	* rather than the name of the class for the instance referred to by the reference.
	* For the polymorphic version of this method, use {@link #getClassName()}.
	*/
	public static String get_class_name() { return "InteractionRoot.C2WInteractionRoot.TestInteraction"; }

	/**
	* Returns the simple name (the last name in the dot-delimited fully-qualified
	* class name) of the TestInteraction interaction class.
	*/
	public static String get_simple_class_name() { return "TestInteraction"; }

	private static Set< String > _datamemberNames = new HashSet< String >();
	private static Set< String > _allDatamemberNames = new HashSet< String >();

	/**
	* Returns a set containing the names of all of the non-hidden parameters in the
	* TestInteraction interaction class.
	* Note: As this is a static method, it is NOT polymorphic, and so, if called on
	* a reference will return a set of parameter names pertaining to the reference,\
	* rather than the parameter names of the class for the instance referred to by
	* the reference.  For the polymorphic version of this method, use
	* {@link #getParameterNames()}.
	*/
	public static Set< String > get_parameter_names() {
		return new HashSet< String >(_datamemberNames);
	}


	/**
	* Returns a set containing the names of all of the parameters in the
	* TestInteraction interaction class.
	* Note: As this is a static method, it is NOT polymorphic, and so, if called on
	* a reference will return a set of parameter names pertaining to the reference,\
	* rather than the parameter names of the class for the instance referred to by
	* the reference.  For the polymorphic version of this method, use
	* {@link #getParameterNames()}.
	*/
	public static Set< String > get_all_parameter_names() {
		return new HashSet< String >(_allDatamemberNames);
	}


	

	static {
		_classNameSet.add("InteractionRoot.C2WInteractionRoot.TestInteraction");
		_classNameClassMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction", TestInteraction.class);
		
		_datamemberClassNameSetMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction", _datamemberNames);
		_allDatamemberClassNameSetMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction", _allDatamemberNames);

		
		
		
		_datamemberNames.add("booleanValue");
		_datamemberNames.add("doubleValue");
		
		_datamemberNames.add("intValue");
		
		
		_datamemberNames.add("stringValue");
		
		
		_allDatamemberNames.add("actualLogicalGenerationTime");
		_allDatamemberNames.add("booleanValue");
		_allDatamemberNames.add("doubleValue");
		_allDatamemberNames.add("federateFilter");
		_allDatamemberNames.add("intValue");
		_allDatamemberNames.add("originFed");
		_allDatamemberNames.add("sourceFed");
		_allDatamemberNames.add("stringValue");
		
		
		_datamemberTypeMap.put("booleanValue", "boolean");
		_datamemberTypeMap.put("doubleValue", "double");
		_datamemberTypeMap.put("intValue", "int");
		_datamemberTypeMap.put("stringValue", "String");
	
	

	}


	private static String initErrorMessage = "Error:  InteractionRoot.C2WInteractionRoot.TestInteraction:  could not initialize:  ";
	protected static void init(RTIambassador rti) {
		if (_isInitialized) return;
		_isInitialized = true;
		
		C2WInteractionRoot.init(rti);
		
		boolean isNotInitialized = true;
		while(isNotInitialized) {
			try {
				_handle = rti.getInteractionClassHandle("InteractionRoot.C2WInteractionRoot.TestInteraction");
				isNotInitialized = false;
			} catch (FederateNotExecutionMember f) {
				logger.error("{} Federate Not Execution Member", initErrorMessage);
				logger.error(f);
				return;				
			} catch (NameNotFound n) {
				logger.error("{} Name Not Found", initErrorMessage);
				logger.error(n);
				return;				
			} catch (Exception e) {
				logger.error(e);
				CpswtUtils.sleepDefault();
			}
		}

		_classNameHandleMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction", get_handle());
		_classHandleNameMap.put(get_handle(), "InteractionRoot.C2WInteractionRoot.TestInteraction");
		_classHandleSimpleNameMap.put(get_handle(), "TestInteraction");

		
		isNotInitialized = true;
		while(isNotInitialized) {
			try {
							
				_booleanValue_handle = rti.getParameterHandle("booleanValue", get_handle());			
				_doubleValue_handle = rti.getParameterHandle("doubleValue", get_handle());			
				_intValue_handle = rti.getParameterHandle("intValue", get_handle());			
				_stringValue_handle = rti.getParameterHandle("stringValue", get_handle());
				isNotInitialized = false;
			} catch (FederateNotExecutionMember f) {
				logger.error("{} Federate Not Execution Member", initErrorMessage);
				logger.error(f);
				return;
			} catch (InteractionClassNotDefined i) {
				logger.error("{} Interaction Class Not Defined", initErrorMessage);
				logger.error(i);
				return;
			} catch (NameNotFound n) {
				logger.error("{} Name Not Found", initErrorMessage);
				logger.error(n);
				return;
			} catch (Exception e) {
				logger.error(e);
				CpswtUtils.sleepDefault();
			}
		}
			
			
		_datamemberNameHandleMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction,booleanValue", get_booleanValue_handle());
		_datamemberNameHandleMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction,doubleValue", get_doubleValue_handle());
		_datamemberNameHandleMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction,intValue", get_intValue_handle());
		_datamemberNameHandleMap.put("InteractionRoot.C2WInteractionRoot.TestInteraction,stringValue", get_stringValue_handle());
			
			
		_datamemberHandleNameMap.put(get_booleanValue_handle(), "booleanValue");
		_datamemberHandleNameMap.put(get_doubleValue_handle(), "doubleValue");
		_datamemberHandleNameMap.put(get_intValue_handle(), "intValue");
		_datamemberHandleNameMap.put(get_stringValue_handle(), "stringValue");
		
	}

	private static boolean _isPublished = false;
	private static String publishErrorMessage = "Error:  InteractionRoot.C2WInteractionRoot.TestInteraction:  could not publish:  ";

	/**
	* Publishes the TestInteraction interaction class for a federate.
	*
	* @param rti handle to the Local RTI Component
	*/
	public static void publish(RTIambassador rti) {
		if (_isPublished) return;
		
		init(rti);

	

		synchronized(rti) {
			boolean isNotPublished = true;
			while(isNotPublished) {
				try {
					rti.publishInteractionClass(get_handle());
					isNotPublished = false;
				} catch (FederateNotExecutionMember f) {
					logger.error("{} Federate Not Execution Member", publishErrorMessage);
					logger.error(f);
					return;
				} catch (InteractionClassNotDefined i) {
					logger.error("{} Interaction Class Not Defined", publishErrorMessage);
					logger.error(i);
					return;
				} catch (Exception e) {
					logger.error(e);
					CpswtUtils.sleepDefault();
				}
			}
		}
		
		_isPublished = true;
	}

	private static String unpublishErrorMessage = "Error:  InteractionRoot.C2WInteractionRoot.TestInteraction:  could not unpublish:  ";
	/**
	* Unpublishes the TestInteraction interaction class for a federate.
	*
	* @param rti handle to the Local RTI Component
	*/
	public static void unpublish(RTIambassador rti) {
		if (!_isPublished) return;
		
		init(rti);
		synchronized(rti) {
			boolean isNotUnpublished = true;
			while(isNotUnpublished) {
				try {
					rti.unpublishInteractionClass(get_handle());
					isNotUnpublished = false;
				} catch (FederateNotExecutionMember f) {
					logger.error("{} Federate Not Execution Member", unpublishErrorMessage);
					logger.error(f);
					return;
				} catch (InteractionClassNotDefined i) {
					logger.error("{} Interaction Class Not Defined", unpublishErrorMessage);
					logger.error(i);
					return;
				} catch (InteractionClassNotPublished i) {
					logger.error("{} Interaction Class Not Published", unpublishErrorMessage);
					logger.error(i);
					return;
				} catch (Exception e) {
					logger.error(e);
					CpswtUtils.sleepDefault();
				}
			}
		}
		
		_isPublished = false;
	}

	private static boolean _isSubscribed = false;
	private static String subscribeErrorMessage = "Error:  InteractionRoot.C2WInteractionRoot.TestInteraction:  could not subscribe:  ";
	/**
	* Subscribes a federate to the TestInteraction interaction class.
	*
	* @param rti handle to the Local RTI Component
	*/
	public static void subscribe(RTIambassador rti) {
		if (_isSubscribed) return;
		
		init(rti);
	
		
		synchronized(rti) {
			boolean isNotSubscribed = true;
			while(isNotSubscribed) {
				try {
					rti.subscribeInteractionClass(get_handle());
					isNotSubscribed = false;
				} catch (FederateNotExecutionMember f) {
					logger.error("{} Federate Not Execution Member", subscribeErrorMessage);
					logger.error(f);
					return;
				} catch (InteractionClassNotDefined i) {
					logger.error("{} Interaction Class Not Defined", subscribeErrorMessage);
					logger.error(i);
					return;
				} catch (Exception e) {
					logger.error(e);
					CpswtUtils.sleepDefault();
				}
			}
		}
		
		_isSubscribed = true;
	}

	private static String unsubscribeErrorMessage = "Error:  InteractionRoot.C2WInteractionRoot.TestInteraction:  could not unsubscribe:  ";
	/**
	* Unsubscribes a federate from the TestInteraction interaction class.
	*
	* @param rti handle to the Local RTI Component
	*/
	public static void unsubscribe(RTIambassador rti) {
		if (!_isSubscribed) return;

		init(rti);
		synchronized(rti) {
			boolean isNotUnsubscribed = true;
			while(isNotUnsubscribed) {
				try {
					rti.unsubscribeInteractionClass(get_handle());
					isNotUnsubscribed = false;
				} catch (FederateNotExecutionMember f) {
					logger.error("{} Federate Not Execution Member", unsubscribeErrorMessage);
					logger.error(f);
					return;
				} catch (InteractionClassNotDefined i) {
					logger.error("{} Interaction Class Not Defined", unsubscribeErrorMessage);
					logger.error(i);
					return;
				} catch (InteractionClassNotSubscribed i) {
					logger.error("{} Interaction Class Not Subscribed", unsubscribeErrorMessage);
					logger.error(i);
					return;
				} catch (Exception e) {
					logger.error(e);
					CpswtUtils.sleepDefault();
				}
			}
		}
		
		_isSubscribed = false;
	}

	/**
	* Return true if "handle" is equal to the handle (RTI assigned) of this class
	* (that is, the TestInteraction interaction class).
	*
	* @param handle handle to compare to the value of the handle (RTI assigned) of
	* this class (the TestInteraction interaction class).
	* @return "true" if "handle" matches the value of the handle of this class
	* (that is, the TestInteraction interaction class).
	*/
	public static boolean match(int handle) { return handle == get_handle(); }

	/**
	* Returns the handle (RTI assigned) of this instance's interaction class .
	* 
	* @return the handle (RTI assigned) if this instance's interaction class
	*/
	public int getClassHandle() { return get_handle(); }

	/**
	* Returns the fully-qualified (dot-delimited) name of this instance's interaction class.
	* 
	* @return the fully-qualified (dot-delimited) name of this instance's interaction class
	*/
	public String getClassName() { return get_class_name(); }

	/**
	* Returns the simple name (last name in its fully-qualified dot-delimited name)
	* of this instance's interaction class.
	* 
	* @return the simple name of this instance's interaction class 
	*/
	public String getSimpleClassName() { return get_simple_class_name(); }

	/**
	* Returns a set containing the names of all of the non-hiddenparameters of an
	* interaction class instance.
	*
	* @return set containing the names of all of the parameters of an
	* interaction class instance
	*/
	public Set< String > getParameterNames() { return get_parameter_names(); }

	/**
	* Returns a set containing the names of all of the parameters of an
	* interaction class instance.
	*
	* @return set containing the names of all of the parameters of an
	* interaction class instance
	*/
	public Set< String > getAllParameterNames() { return get_all_parameter_names(); }

	/**
	* Publishes the interaction class of this instance of the class for a federate.
	*
	* @param rti handle to the Local RTI Component
	*/
	public void publishInteraction(RTIambassador rti) { publish(rti); }

	/**
	* Unpublishes the interaction class of this instance of this class for a federate.
	*
	* @param rti handle to the Local RTI Component
	*/
	public void unpublishInteraction(RTIambassador rti) { unpublish(rti); }

	/**
	* Subscribes a federate to the interaction class of this instance of this class.
	*
	* @param rti handle to the Local RTI Component
	*/
	public void subscribeInteraction(RTIambassador rti) { subscribe(rti); }

	/**
	* Unsubscribes a federate from the interaction class of this instance of this class.
	*
	* @param rti handle to the Local RTI Component
	*/
	public void unsubscribeInteraction(RTIambassador rti) { unsubscribe(rti); }

	

	public String toString() {
		return "TestInteraction("
			
			
			+ "booleanValue:" + get_booleanValue()
			+ "," + "doubleValue:" + get_doubleValue()
			+ "," + "intValue:" + get_intValue()
			+ "," + "stringValue:" + get_stringValue()
			+ ")";
	}
	



	
	
	private boolean _booleanValue = false;
	
	private double _doubleValue = 0;
	
	private int _intValue = 0;
	
	private String _stringValue = "";

	
	
	/**
	* Set the value of the "booleanValue" parameter to "value" for this parameter.
	*
	* @param value the new value for the "booleanValue" parameter
	*/
	public void set_booleanValue( boolean value ) { _booleanValue = value; }
	
	/**
	* Returns the value of the "booleanValue" parameter of this interaction.
	*
	* @return the value of the "booleanValue" parameter
	*/
	public boolean get_booleanValue() { return _booleanValue; }
	
	
	/**
	* Set the value of the "doubleValue" parameter to "value" for this parameter.
	*
	* @param value the new value for the "doubleValue" parameter
	*/
	public void set_doubleValue( double value ) { _doubleValue = value; }
	
	/**
	* Returns the value of the "doubleValue" parameter of this interaction.
	*
	* @return the value of the "doubleValue" parameter
	*/
	public double get_doubleValue() { return _doubleValue; }
	
	
	/**
	* Set the value of the "intValue" parameter to "value" for this parameter.
	*
	* @param value the new value for the "intValue" parameter
	*/
	public void set_intValue( int value ) { _intValue = value; }
	
	/**
	* Returns the value of the "intValue" parameter of this interaction.
	*
	* @return the value of the "intValue" parameter
	*/
	public int get_intValue() { return _intValue; }
	
	
	/**
	* Set the value of the "stringValue" parameter to "value" for this parameter.
	*
	* @param value the new value for the "stringValue" parameter
	*/
	public void set_stringValue( String value ) { _stringValue = value; }
	
	/**
	* Returns the value of the "stringValue" parameter of this interaction.
	*
	* @return the value of the "stringValue" parameter
	*/
	public String get_stringValue() { return _stringValue; }
	


	protected TestInteraction( ReceivedInteraction datamemberMap, boolean initFlag ) {
		super( datamemberMap, false );
		if ( initFlag ) setParameters( datamemberMap );
	}
	
	protected TestInteraction( ReceivedInteraction datamemberMap, LogicalTime logicalTime, boolean initFlag ) {
		super( datamemberMap, logicalTime, false );
		if ( initFlag ) setParameters( datamemberMap );
	}


	/**
	* Creates an instance of the TestInteraction interaction class, using
	* "datamemberMap" to initialize its parameter values.
	* "datamemberMap" is usually acquired as an argument to an RTI federate
	* callback method, such as "receiveInteraction".
	*
	* @param datamemberMap data structure containing initial values for the
	* parameters of this new TestInteraction interaction class instance
	*/
	public TestInteraction( ReceivedInteraction datamemberMap ) {
		this( datamemberMap, true );
	}
	
	/**
	* Like {@link #TestInteraction( ReceivedInteraction datamemberMap )}, except this
	* new TestInteraction interaction class instance is given a timestamp of
	* "logicalTime".
	*
	* @param datamemberMap data structure containing initial values for the
	* parameters of this new TestInteraction interaction class instance
	* @param logicalTime timestamp for this new TestInteraction interaction class
	* instance
	*/
	public TestInteraction( ReceivedInteraction datamemberMap, LogicalTime logicalTime ) {
		this( datamemberMap, logicalTime, true );
	}

	/**
	* Creates a new TestInteraction interaction class instance that is a duplicate
	* of the instance referred to by TestInteraction_var.
	*
	* @param TestInteraction_var TestInteraction interaction class instance of which
	* this newly created TestInteraction interaction class instance will be a
	* duplicate
	*/
	public TestInteraction( TestInteraction TestInteraction_var ) {
		super( TestInteraction_var );
		
		
		set_booleanValue( TestInteraction_var.get_booleanValue() );
		set_doubleValue( TestInteraction_var.get_doubleValue() );
		set_intValue( TestInteraction_var.get_intValue() );
		set_stringValue( TestInteraction_var.get_stringValue() );
	}


	/**
	* Returns the value of the parameter whose name is "datamemberName"
	* for this interaction.
	*
	* @param datamemberName name of parameter whose value is to be
	* returned
	* @return value of the parameter whose name is "datamemberName"
	* for this interaction
	*/
	public Object getParameter( String datamemberName ) {
		
		
		
		if (  "booleanValue".equals( datamemberName )  ) return new Boolean(get_booleanValue());
		else if (  "doubleValue".equals( datamemberName )  ) return new Double(get_doubleValue());
		else if (  "intValue".equals( datamemberName )  ) return new Integer(get_intValue());
		else if (  "stringValue".equals( datamemberName )  ) return get_stringValue();
		else return super.getParameter( datamemberName );
	}
	
	/**
	* Returns the value of the parameter whose handle (RTI assigned)
	* is "datamemberHandle" for this interaction.
	*
	* @param datamemberHandle handle (RTI assigned) of parameter whose
	* value is to be returned
	* @return value of the parameter whose handle (RTI assigned) is
	* "datamemberHandle" for this interaction
	*/
	public Object getParameter( int datamemberHandle ) {
		
				
		
		if ( get_booleanValue_handle() == datamemberHandle ) return new Boolean(get_booleanValue());
		else if ( get_doubleValue_handle() == datamemberHandle ) return new Double(get_doubleValue());
		else if ( get_intValue_handle() == datamemberHandle ) return new Integer(get_intValue());
		else if ( get_stringValue_handle() == datamemberHandle ) return get_stringValue();
		else return super.getParameter( datamemberHandle );
	}
	
	protected boolean setParameterAux( int param_handle, String val ) {
		boolean retval = true;		
		
			
		
		if ( param_handle == get_booleanValue_handle() ) set_booleanValue( Boolean.parseBoolean(val) );
		else if ( param_handle == get_doubleValue_handle() ) set_doubleValue( Double.parseDouble(val) );
		else if ( param_handle == get_intValue_handle() ) set_intValue( Integer.parseInt(val) );
		else if ( param_handle == get_stringValue_handle() ) set_stringValue( val );
		else retval = super.setParameterAux( param_handle, val );
		
		return retval;
	}
	
	protected boolean setParameterAux( String datamemberName, String val ) {
		boolean retval = true;
		
			
		
		if (  "booleanValue".equals( datamemberName )  ) set_booleanValue( Boolean.parseBoolean(val) );
		else if (  "doubleValue".equals( datamemberName )  ) set_doubleValue( Double.parseDouble(val) );
		else if (  "intValue".equals( datamemberName )  ) set_intValue( Integer.parseInt(val) );
		else if (  "stringValue".equals( datamemberName )  ) set_stringValue( val );	
		else retval = super.setParameterAux( datamemberName, val );
		
		return retval;
	}
	
	protected boolean setParameterAux( String datamemberName, Object val ) {
		boolean retval = true;
		
		
		
		if (  "booleanValue".equals( datamemberName )  ) set_booleanValue( (Boolean)val );
		else if (  "doubleValue".equals( datamemberName )  ) set_doubleValue( (Double)val );
		else if (  "intValue".equals( datamemberName )  ) set_intValue( (Integer)val );
		else if (  "stringValue".equals( datamemberName )  ) set_stringValue( (String)val );		
		else retval = super.setParameterAux( datamemberName, val );
		
		return retval;
	}

	protected SuppliedParameters createSuppliedDatamembers() {
		SuppliedParameters datamembers = super.createSuppliedDatamembers();

	
		
		
			datamembers.add( get_booleanValue_handle(), Boolean.toString(get_booleanValue()).getBytes() );
		
			datamembers.add( get_doubleValue_handle(), Double.toString(get_doubleValue()).getBytes() );
		
			datamembers.add( get_intValue_handle(), Integer.toString(get_intValue()).getBytes() );
		
			datamembers.add( get_stringValue_handle(), get_stringValue().getBytes() );
		
	
		return datamembers;
	}

	
	public void copyFrom( Object object ) {
		super.copyFrom( object );
		if ( object instanceof TestInteraction ) {
			TestInteraction data = (TestInteraction)object;
			
			
				_booleanValue = data._booleanValue;
				_doubleValue = data._doubleValue;
				_intValue = data._intValue;
				_stringValue = data._stringValue;
			
		}
	}
}
