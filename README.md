MAT Calcite plugin
==================

About
-----
This plugin for [Eclipse Memory Analyzer](http://www.eclipse.org/mat) allows to query heap dump via SQL

While MAT does have a query language, it does NOT allow to join, sort and group results.
MAT Calcite plugin allows all the typical SQL operations (joins, filters, group by, order by, etc)

Query engine is implemented via [Apache Calcite](http://calcite.incubator.apache.org)
See [Calcite SQL reference](https://github.com/apache/incubator-calcite/blob/master/doc/REFERENCE.md)

Installation
------------

Use the following update repository to install the latest released version: http://dl.bintray.com/vlsi/eclipse/

Sample
------

Query that lists duplicate URLs:

```sql
select toString(file) file, count(*) cnt, sum("@RETAINED") sum_retained, sum("@SHALLOW") sum_shallow
  from "java.net.URL"
 group by toString(file)
having count(*)>1
 order by sum("@RETAINED") desc
```

To get an explain plan, use "explain plan for select ...":

```
EnumerableSortRel(sort0=[$2], dir0=[DESC])
  EnumerableCalcRel(expr#0..3=[{inputs}], expr#4=[1], expr#5=[>($t1, $t4)], proj#0..3=[{exprs}], $condition=[$t5])
    EnumerableAggregateRel(group=[{0}], cnt=[COUNT()], sum_retained=[SUM($1)], sum_shallow=[SUM($2)])
      EnumerableCalcRel(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2, $t0)], expr#4=['file'], expr#5=[RESOLVE_REFERENCE($t3, $t4)], expr#6=[toString($t5)], expr#7=[GET_RETAINED_SIZE($t2, $t0)], expr#8=[GET_SHALLOW_SIZE($t2, $t0)], file=[$t6], @RETAINED=[$t7], @SHALLOW=[$t8])
        EnumerableTableAccessRel(table=[[HEAP, $ids$:java.net.URL]])
```

Join sample
-----------

```sql
explain plan for
 select u."@THIS", s."@RETAINED"
   from "java.lang.String" s
   join "java.net.URL" u
     on s."@THIS" = u.path
```

Here's execution plan:

```
EnumerableCalcRel(expr#0..3=[{inputs}], @ID=[$t0], @RETAINED=[$t3])
  EnumerableJoinRel(condition=[=($1, $2)], joinType=[inner])
    EnumerableCalcRel(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_REFERENCE($t2, $t0)], expr#4=[GET_IOBJECT($t2, $t0)], expr#5=['path'], expr#6=[RESOLVE_REFERENCE($t4, $t5)], @THIS=[$t3], path=[$t6])
      EnumerableTableAccessRel(table=[[HEAP, $ids$:java.net.URL]])
    EnumerableCalcRel(expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_REFERENCE($t2, $t0)], expr#4=[GET_RETAINED_SIZE($t2, $t0)], @THIS=[$t3], @RETAINED=[$t4])
      EnumerableTableAccessRel(table=[[HEAP, $ids$:java.lang.String]])
```

Here we do not yet optimize `s."@ID" = get_id(u.path)` join to `snapshot.getObject(get_id(u.path))`, however non-required columns are eliminated.

Heap schema
-----------

### Each java class maps to a table. The table lists instances without subclasses
 For instance: "java.lang.Object", "java.lang.String"
 Note: you need to use double quotes to quote identifiers
 Note: it is assumed that classes sharing the same name have the same fields

 The fields become columns.

 The following special columns are added:

    @THIS     | reference to current object
    @SHALLOW  | shallow heap size of current instance
    @RETAINED | retained heap size of current instance

 The following functions can be used to work with column which represents reference:

    get_id    | internal object identifier for referenced object
    get_type  | class name of referenced object
    toString  | textual representation of referenced object
    length    | length of referenced array
    get_by_id | extracts value for given string representation of key for referenced HashMap

Requirements
------------
Java 1.7


Building
--------

Eclipse plugin cannot depend on jars from maven repository.
It has to be a OSGi bundle, however Calcite is easier to reach via maven.
So we use two-phase approach: bundle the dependencies in a single jar, then use this jar in eclipse project.

1. Build dependencies.jar

    ```
    cd dependencies
    mvn install
    ```

    This will create a jar with all the dependencies in `dependencies/target` folder.
    You do not need to touch/move/copy the jar.

2. Build the plugin

    ```
    mvn install # from the top-level folder
    ```

    Note: this will copy `dependencies.jar` to `MatCalcitePlugin/MatCalcitePlugin/target/dependency` so Eclipse can find it.

    The final repository (aka "update site") with the plugin will be created in `eclipse-repository/target/eclipse-repository-1.0.0-SNAPSHOT.zip`


Running
-------

It is not yet clear how to run the plugin via maven.
To launch via Eclipse, just open Eclipse project, double-click `plugin.xml`, then click `Launch an Eclipse application`.

Roadmap
-------

- enhance heap schema
- context menu support
- heap-related filtering operators (instance of, dominator of, etc)
- interoperation with mat queries (histogram, retained set, etc)
- support external data providers (e.g. allow to join heapdump objects with csv file)
- projection support (do not compute unnecessary columns)
- optimizer rules

License
-------
This library is distributed under terms of Apache 2 License

Change log
----------
v1.1.1
  Updated to Apache Calcite 0.9.1, switch to double-quotes, enable maven-only build

v1.1
  Undo/redo support, syntax highlighting

v1.0.1
  Updated optiq to 0.4.18: enable case-sensitive identifiers by default, back-tick quotes

v1.0.0
  Proof of concept.

Author
------
Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
