package ma.inpt.stages.common;

import java.time.LocalDate;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/** Conversion entre xs:date et LocalDate. */
public final class Dates {

    private static final DatatypeFactory FABRIQUE = creer();

    private Dates() {
    }

    private static DatatypeFactory creer() {
        try {
            return DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static LocalDate versLocalDate(XMLGregorianCalendar date) {
        return date == null ? null : LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
    }

    public static XMLGregorianCalendar versXml(LocalDate date) {
        return date == null ? null : FABRIQUE.newXMLGregorianCalendar(date.toString());
    }
}
