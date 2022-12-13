package com.equalize.converter.core

import com.equalize.converter.core.fcc.RecordTypeParametersFactory
import com.equalize.converter.core.fcc.RecordTypeParametersXML2Plain
import com.equalize.converter.core.util.AbstractConverter
import com.equalize.converter.core.util.ClassTypeConverter
import com.equalize.converter.core.util.ConversionDOMInput
import com.equalize.converter.core.util.ConversionPlainOutput
import com.equalize.converter.core.util.ConversionSAXInput
import com.equalize.converter.core.util.ConverterException
import com.equalize.converter.core.util.Field
import com.equalize.converter.core.util.XMLElementContainer

class XML2DeepPlainConverter extends AbstractConverter {
	ConversionPlainOutput plainOut
	XMLElementContainer rootXML
	String encoding
	boolean useDOM
	boolean trim
	final Map<String, RecordTypeParametersXML2Plain> recordTypes

	XML2DeepPlainConverter(Object body, Map<String,Object> properties, ClassTypeConverter typeConverter) {
		super(body, properties, typeConverter)
		this.recordTypes = new HashMap<String, RecordTypeParametersXML2Plain>()
	}

	@Override
	void retrieveParameters(){
		this.encoding = this.ph.retrieveProperty('encoding', 'UTF-8')
		this.useDOM = this.ph.retrievePropertyAsBoolean('useDOM', 'N')
		this.trim = this.ph.retrievePropertyAsBoolean('trim', 'N')
		String recordsetStructure = this.ph.retrieveProperty('recordsetStructure')

		String[] recordsetList = recordsetStructure.split(',')
		recordsetList.each { recordTypeName ->
			if (!this.recordTypes.containsKey(recordTypeName)) {
				RecordTypeParametersXML2Plain rtp = (RecordTypeParametersXML2Plain) RecordTypeParametersFactory
						.newInstance()
						.newParameter(recordTypeName, recordsetList, this.encoding, this.ph, 'xml2plain')
				rtp.storeAdditionalParameters(recordTypeName, this.ph, this.encoding)
				this.recordTypes.put(recordTypeName, rtp)
			} else {
				throw new ConverterException("Duplicate field found in 'recordsetStructure': $recordTypeName")
			}
		}
	}

	@Override
	void parseInput() {
		// Parse input XML contents		
		if (this.useDOM) {
			def is =  this.typeConverter.convertTo(InputStream, this.body)
			ConversionDOMInput domIn = new ConversionDOMInput(is)
			this.rootXML = domIn.extractDOMContent(this.trim)
		} else {
			def reader =  this.typeConverter.convertTo(Reader, this.body)
			ConversionSAXInput saxIn = new ConversionSAXInput(reader)
			this.rootXML = saxIn.extractXMLContent(this.trim)
		}
	}

	@Override
	Object generateOutput() {
		// Create output converter and generate output flat content
		this.plainOut = new ConversionPlainOutput()

		constructTextfromXML(this.rootXML, true).getBytes(encoding)
	}

	private String constructTextfromXML(XMLElementContainer element, boolean isRoot) {
		StringBuilder sb = new StringBuilder()
		// First, construct output for current element's child fields
		if (!isRoot) {
			sb << generateRowTextForElement(element)
		}
		// Then recursively process child elements that are segments
		element.getChildFields().findAll { it.fieldContent instanceof XMLElementContainer }.each {
			sb << constructTextfromXML((XMLElementContainer) it.fieldContent, false)
		}
		return sb.toString()
	}

	private String generateRowTextForElement(XMLElementContainer element) {
		
		RecordTypeParametersXML2Plain rtp = this.recordTypes.get(segmentName)
		if (rtp.seledtedFields != null) {
			List<Field> childFields = rtp.seledtedFields
		}else{	
			List<Field> childFields = element.getChildFields()
		}	
		String segmentName = element.getElementName()
		if (!this.recordTypes.containsKey(segmentName)) {
			throw new ConverterException("Record Type $segmentName not listed in parameter 'recordsetStructure'")
		}
		RecordTypeParametersXML2Plain rtp = this.recordTypes.get(segmentName)
		if (rtp.fixedLengths != null) {
			checkFieldCountConsistency(segmentName, childFields, rtp.fixedLengths.length)
		}
		return this.plainOut.generateLineText(childFields, rtp.fieldSeparator, rtp.fixedLengths, rtp.endSeparator,
				rtp.fixedLengthTooShortHandling, rtp.enclosureSign, rtp.enclosureSignEscape)
	}

	private void checkFieldCountConsistency(String segmentName, List<Field> childFields, int noOfColumns) {
		int leafFieldCount = childFields.findAll { it.fieldContent instanceof String }.size()
		if (leafFieldCount > noOfColumns)
			throw new ConverterException("More fields found in XML structure than specified in parameter '${segmentName}.fieldFixedLengths'")
	}
}
