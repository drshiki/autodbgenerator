package moe.dam.autodbgenerator
aa
import org.gradle.api.Plugin
import org.gradle.api.Project

class Autodbgenerator implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println("use Autodbgenerator")

        project.task('generateUpdateDbScript') {
            dependsOn("classes")
            println "@@my name is ${name}"
            doLast {
                println "my name is ${name}"

                def classLoader = new GroovyClassLoader()
                classLoader.addClasspath("${project.sourceSets.main.output.classesDir}")
                project.configurations.compile.each { classLoader.addClasspath(it.path) }

                def cTree = project.fileTree(dir: project.sourceSets.main.output.classesDir)

                def classFiles = cTree.matching {
                    include '**/*.class'
                    exclude '**/*$*.class'
                }

                def entities = [:]

                classFiles.each { f ->
                    def c = f.path - (project.sourceSets.main.output.classesDir.path + "\\")
                    def className = c.replaceAll('\\\\', '\\.') - ".class"

                    def cz = Class.forName(className, false, classLoader)

                    def tableAnnotate = Class.forName("javax.persistence.Table", false, classLoader)
                    def columnAnnotate = Class.forName("javax.persistence.Column", false, classLoader)
                    def joinColumnAnnotate = Class.forName("javax.persistence.JoinColumn", false, classLoader)

                    if (cz.isAnnotationPresent(tableAnnotate)) {
                        def p = cz
                        def cols = [:]
                        while (p != null) {
                            p.declaredFields.each {
                                if (it.isAnnotationPresent(columnAnnotate)) {
                                    def an = it.getAnnotation(columnAnnotate)
                                    cols << [
                                            [["name": it.name], ["type": it.type]]:
                                                    [["column_name": an?.name() ?: ''],
                                                     ["length": an.length()],
                                                     ["precision": an.precision()],
                                                     ["scale": an.scale()],
                                                     ["nullable": an.nullable()]]
                                    ]

                                } else if (it.isAnnotationPresent(joinColumnAnnotate)) {
                                    def an = it.getAnnotation(joinColumnAnnotate)
                                    cols << [
                                            [["name": it.name], ["type": it.type]]:
                                                    [["column_name": an?.name() ?: ''],
                                                     ["type": "varchar"],
                                                     ["length": 32]]
                                    ]
                                }
                            }
                            p = p.getSuperclass()
                            entities.put([cz.name, cz.getAnnotation(tableAnnotate).name()], cols)
                        }
                        println(cz.name)
                    }
                }

                generateScript(entities)
            }
        }
        println(" here is end")
    }

    def generateScript(Map entitiesDef) {

        def typeMap = [:]
        typeMap.put(String.class, "varchar")
        typeMap.put(Date.class, "datetime")
        typeMap.put(Integer.class, "integer")
        typeMap.put(BigDecimal.class, "decimal")
        typeMap.put(Boolean.class, "boolean")

        entitiesDef.each {
            def tableName = it.key[1]
            def cols = it.value
            def sql
            cols.each { col ->
                sql += "ALTER TABLE ${tableName} ADD COLUMN " +
                        col.value[0]["column_name"] + " " +
                        (typeMap[col.key[1]["type"]]?:col.value[1]["type"]) +
                        "(" + col.value[2]["length"] + ");\n"
            }

            println(sql)
        }

    }
}
