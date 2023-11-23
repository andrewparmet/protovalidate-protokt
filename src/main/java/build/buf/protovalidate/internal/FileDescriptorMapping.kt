package build.buf.protovalidate.internal


import io.github.classgraph.ClassGraph
import protokt.v1.KtGeneratedFileDescriptor
import protokt.v1.google.protobuf.Descriptor
import protokt.v1.google.protobuf.FileDescriptor
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * Contains a mapping from symbol names to file descriptor objects, and from file descriptor names to file descriptor objects.
 *
 * We start with a list of ServerServiceDefinitions from the server.
 * Then, we look at the file descriptors for each service, as well as all its (recursive) dependencies.
 * We build a mapping for all the types in these file descriptors.
 *
 * In rare cases, we might need a type that isn't referenced by any of the file descriptors we found above. This will
 * mainly be an issue with `Any` fields. In this case, we scan the whole class graph for file descriptors, which can be
 * slow. We only have to do this once though, and we cache the result.
 */
internal interface FileDescriptorMapping {
    fun fileDescriptorForSymbol(symbolName: String): FileDescriptor?
    fun fileDescriptorForFileName(fileDescriptorName: String): FileDescriptor?
}

class FileDescriptorMappingImpl() : FileDescriptorMapping {
    private val fallbackFileDescriptorMapping by lazy { FallbackFileDescriptorMapping() }

    override fun fileDescriptorForSymbol(symbolName: String): FileDescriptor? =
        fallbackFileDescriptorMapping.fileDescriptorForSymbol(symbolName)

    override fun fileDescriptorForFileName(fileDescriptorName: String): FileDescriptor? =
        fallbackFileDescriptorMapping.fileDescriptorForName(fileDescriptorName)
}

/**
 * Contains a mapping of services, messages, and enums to the file descriptors that contain them.
 */
private abstract class AbstractFileDescriptorMapping() {

    abstract val allFileDescriptors: Iterable<FileDescriptor>

    val fileDescriptorsBySymbol: Map<String, FileDescriptor> by lazy {
        allFileDescriptors.flatMap { fileDescriptor ->
            fileDescriptor.services.map { service -> service.fullName to fileDescriptor } +
                    fileDescriptor.allTypes().map { type -> type.fullName to fileDescriptor } +
                    fileDescriptor.enumTypes.map { type -> type.fullName to fileDescriptor }
        }.toMap()
    }

    val fileDescriptorsByName by lazy { allFileDescriptors.associateBy { fileDescriptor -> fileDescriptor.proto.name!! } }

    val serviceNames by lazy { allFileDescriptors.flatMap { fileDescriptor -> fileDescriptor.services.map { it.fullName } }.toSet() }

    fun fileDescriptorForSymbol(serviceName: String) = fileDescriptorsBySymbol[serviceName]

    fun fileDescriptorForName(fileDescriptorName: String) = fileDescriptorsByName[fileDescriptorName]
}

@Suppress("UNCHECKED_CAST")
private class FallbackFileDescriptorMapping() : AbstractFileDescriptorMapping() {

    override val allFileDescriptors: Iterable<FileDescriptor> by lazy {
        ClassGraph()
            .enableAllInfo()
            .scan()
            .getClassesWithAnnotation(KtGeneratedFileDescriptor::class.java)
            .map { it.loadClass().kotlin as KClass<Any> }
            .map { fileDescriptorContainer ->
                val property = fileDescriptorContainer.memberProperties.find { it.name == "descriptor" }!!
                property.get(fileDescriptorContainer.objectInstance!!) as FileDescriptor
            }
    }
}

private fun List<FileDescriptor>.allTransitiveDependencies(): Set<FileDescriptor> {
    val seenFiles = this@allTransitiveDependencies.map { it.proto.name!! }.toMutableSet()

    fun helper(fileDescriptor: FileDescriptor): Set<FileDescriptor> =
        fileDescriptor.dependencies
            .filter { it.proto.name !in seenFiles }
            .onEach { seenFiles.add(it.proto.name!!) }
            .flatMap { helper(it) }
            .toSet() + fileDescriptor

    return this@allTransitiveDependencies.flatMap { helper(it) }.toSet()
}

private fun Descriptor.allNestedTypes(): List<Descriptor> = this.nestedTypes.flatMap { it.allNestedTypes() } + this
private fun FileDescriptor.allTypes(): Set<Descriptor> = this.messageTypes.flatMap { it.allNestedTypes() }.toSet()
