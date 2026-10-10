package practice.lld.firstiteration.simplelld;
import java.util.*;


// table schema
// document/row
// schema validator
// fieldSchema

class ReadQueryFilter{
    String Key_name;
    Object value;
}

class FieldSchema{
    String type;
    String name;
    String limit;
}

class TableSchema{
    List<FieldSchema> fieldSchemasINOrder;
}

class Document{
    int id;
    int tableSchemaId;
    List<Objects> fieldValuesINOrder;
}

class Table{
    int id;
    String name;
    TableSchema tableSchema;
    List<Document> documents;

//    public Table(String name, TableSchema schema){
//        this.id = (int)Math.random(0,100);
//        this.name = name;
//        this.tableSchema = schema;
//    }


}

class DataBaseSimulation{

//    List<Table> tables;
//
//    public void createTable(TableSchema tableSchema, String name){
//        // have a question here doest he random table should be generated here or in the constructor of the table itself
//        // i guess not in the constructor of the table itself because it doesn't know that all tableIds existing
//        // but here we have list of table and we know all the table Ids so we can check to generate new
//        // also how should it be in the lld-machine coding interviews.interview i think there should not be much focus on the generating the random id
//        // but on the structural solution rather
//        Table newTable = new Table(name, tableSchema);
//        this.tables.add(newTable);
//    }
//
//
//    public void insertDocument(int tableId, List<Object> fieldValuesInOrder){
//        // get the table using the Id
//        // validate the fields values with the field Schema
//        // insert into the table documents
//    }
//
//    public void readDocument(int tableId, List<ReadQueryFilter> filter){
//        // get the table using the Id
//        // validate if the each read keyName  and value type is mathing the fieldSchema of that table field name;
//        // apply all filters sequentially
//
//        return the result;
//    }

}

public class SimpleLLD2 {

    public static void main(String[] args){

    }
}
