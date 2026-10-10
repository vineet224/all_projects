package practice.lld.thirditeration.fileSystem;


import java.util.*;

abstract class FileSystemNode {
    String name;
    Directory parent;

    FileSystemNode(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
    }

    String getPath() {
        if (parent == null) return "/";
        String prefix = parent.getPath();
        return prefix.equals("/")
                ? prefix + name
                : prefix + "/" + name;
    }
}

class File extends FileSystemNode {
    StringBuilder content = new StringBuilder();

    File(String name, Directory parent) {
        super(name, parent);
    }
}

class Directory extends FileSystemNode {
    Map<String, FileSystemNode> children = new HashMap<>();

    Directory(String name, Directory parent) {
        super(name, parent);
    }
}

public class FileSystem {
    private final Directory root = new Directory("", null);

    // Resolve an existing path
    private FileSystemNode resolve(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Invalid path");
        }

        FileSystemNode current = root;

        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            if (part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Invalid path");
            }

            if (!(current instanceof Directory dir)) {
                throw new IllegalArgumentException("Not a directory");
            }

            current = dir.children.get(part);

            if (current == null) {
                throw new IllegalArgumentException("Path not found");
            }
        }

        return current;
    }

    // Create directories recursively like mkdir -p
    public void mkdir(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Invalid path");
        }

        Directory current = root;

        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            validateName(part);

            FileSystemNode child = current.children.get(part);

            if (child == null) {
                Directory dir = new Directory(part, current);
                current.children.put(part, dir);
                current = dir;
            } else if (child instanceof Directory dir) {
                current = dir;
            } else {
                throw new IllegalArgumentException("File exists");
            }
        }
    }

    // Create a file; parent directory must exist
    public void createFile(String path) {
        String name = getName(path);
        Directory parent = getParent(path);

        if (parent.children.containsKey(name)) {
            throw new IllegalArgumentException("Already exists");
        }

        parent.children.put(name, new File(name, parent));
    }

    public void writeFile(String path, String content) {
        File file = requireFile(path);
        file.content.append(content);
    }

    public String readFile(String path) {
        return requireFile(path).content.toString();
    }

    public List<String> ls(String path) {
        FileSystemNode node = resolve(path);

        if (node instanceof File) {
            return List.of(node.name);
        }

        Directory dir = (Directory) node;
        List<String> result = new ArrayList<>(dir.children.keySet());
        Collections.sort(result);
        return result;
    }

    // Delete a file or an entire directory subtree
    public void delete(String path) {
        FileSystemNode node = resolve(path);

        if (node == root) {
            throw new IllegalArgumentException("Cannot delete root");
        }

        node.parent.children.remove(node.name);
    }

    // Search by exact name, starting from any directory
    public List<String> search(String dirPath, String name) {
        FileSystemNode node = resolve(dirPath);

        if (!(node instanceof Directory dir)) {
            throw new IllegalArgumentException("Not a directory");
        }

        List<String> result = new ArrayList<>();
        dfs(dir, name, result);
        return result;
    }

    private void dfs(Directory dir, String name,
                     List<String> result) {
        for (FileSystemNode child : dir.children.values()) {
            if (child.name.equals(name)) {
                result.add(child.getPath());
            }

            if (child instanceof Directory subDir) {
                dfs(subDir, name, result);
            }
        }
    }

    private File requireFile(String path) {
        FileSystemNode node = resolve(path);

        if (!(node instanceof File file)) {
            throw new IllegalArgumentException("Not a file");
        }
        return file;
    }

    private Directory getParent(String path) {
        int index = path.lastIndexOf('/');
        String parentPath = index == 0 ? "/" : path.substring(0, index);

        FileSystemNode node = resolve(parentPath);

        if (!(node instanceof Directory dir)) {
            throw new IllegalArgumentException("Invalid parent");
        }
        return dir;
    }

    private String getName(String path) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("Invalid path");
        }

        String name = path.substring(path.lastIndexOf('/') + 1);
        validateName(name);
        return name;
    }

    private void validateName(String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid name");
        }
    }

    public static void main(String[] args) {
        FileSystem fs = new FileSystem();

        fs.mkdir("/home/user/docs");
        fs.mkdir("/home/user/images");

        fs.createFile("/home/user/docs/a.txt");
        fs.createFile("/home/user/images/a.txt");

        fs.writeFile("/home/user/docs/a.txt", "Hello");
        fs.writeFile("/home/user/docs/a.txt", " World");

        System.out.println(fs.readFile("/home/user/docs/a.txt"));
        // Hello World

        System.out.println(fs.ls("/home/user"));
        // [docs, images]

        System.out.println(fs.search("/", "a.txt"));
        // [/home/user/docs/a.txt, /home/user/images/a.txt]
        // Order may vary

        fs.delete("/home/user/docs/a.txt");
        System.out.println(fs.ls("/home/user/docs"));
        // []
    }

}



