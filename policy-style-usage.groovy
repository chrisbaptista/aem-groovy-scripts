/**
 * Policy Style Usage
 *
 * Builds a CSV Report of all Policy Styles and count of Component Instances that are using them
 */
import org.apache.jackrabbit.oak.jcr.query.*
import javax.jcr.query.*


/**
 * Style
 *
 * This class is to help build out the report
 */
class Style {
    /**
     * Component Name
     */
    String componentName

    /**
     * Policy name
     */
    String policyName

    /**
     * Style Group
     */
    String styleGroup

    /**
     * Style ID
     */
    String styleId

    /**
     * Style Name
     */
    String styleName

    /**
     * CSS Classes
     */
    String cssClasses

    /**
     * Count
     */
    int count

    /**
     * To CSV String
     *
     * Helper function to format Style Object for CSV Print
     *
     * @return
     */
    def toCsvString() {
        '"' + componentName + '","' + policyName + '","' + styleGroup + '","' + styleName + '","' + styleId + '","' + cssClasses + '","' + count.toString() + '"'
    }
}

/**
 * Report Class
 *
 * This class is to build out the report for Policy Style Usage
 */
class Report {
    /**
     * Styles List
     *
     * Holds array for Style objects. Used to generate report
     */
    private ArrayList stylesList = []

    /**
     * Styles Count
     *
     * Holds map of Style IDs to their usage count
     */
    private LinkedHashMap stylesCount = [:]

    /**
     * Content Path
     *
     * JCR Path to search for Usage
     */
    private String contentPath

    /**
     * Policy Path
     *
     * JCR Path to policies
     */
    private String policyPath

    /**
     * Session
     *
     * Working Session
     */
    private Session session

    /**
     * Search Node for Styles
     *
     * @param node
     * @return
     */
    private def searchNodeForStyles(Node node) {
        if (node.hasProperty('cq:styleIds')) {
            addStyleIds(node)
        }
        node.nodes.each {
            child ->
                searchNodeForStyles(child)
        }
    }

    /**
     * Add Style ID
     *
     * Add Style ID to stylesCount
     *
     * @param styleId
     * @return
     */
    private def addStyleId(String styleId) {
        if (!stylesCount.containsKey(styleId)) {
            stylesCount[styleId] = 0
        }

        stylesCount[styleId]++
    }

    /**
     * Add Style IDs
     *
     * Gets Style IDs from node
     *
     * @param node
     * @return
     */
    private def addStyleIds(Node node) {
        String property = 'cq:styleIds'

        // Get as Multi-Field Value
        try {
            node.getProperty(property).getValues().each {
                styleId ->
                    addStyleId(styleId.toString())
            }
            return
        } catch (ignored) {}

        // Get as Single-Field Value
        try {
            def styleId = node.getProperty(property).getValue()
            addStyleId(styleId.toString())

        } catch (ignored) {}
    }


    /**
     * Query JCR
     *
     * @param queryStmt
     * @return QueryResult
     */
    private QueryResult queryJcr(String queryStmt) {
        QueryManager queryManager = session.workspace.queryManager
        Query query = queryManager.createQuery(queryStmt, 'JCR-SQL2')
        return query.execute()
    }

    /**
     * Get Policies
     *
     * Get all Policies from Path
     *
     * @param path
     * @return QueryResult
     */
    private QueryResult getPolicies(String path) {
        return queryJcr('select * from [nt:unstructured] as t where ISDESCENDANTNODE([' + path + ']) AND [sling:resourceType]="wcm/core/components/policy/policy"')
    }

    /**
     * Get Style Groups
     *
     * @param path
     * @return QueryResult
     */
    private QueryResult getStyleGroups(String path) {
        return queryJcr('select * from [nt:unstructured] as t where ISDESCENDANTNODE([' + path + ']) AND [cq:styleGroupLabel] LIKE "%"')
    }

    /**
     * Get Styles
     *
     * @param path
     * @return Query Result
     */
    private QueryResult getStyles(String path) {
        return queryJcr('select * from [nt:unstructured] as t where ISDESCENDANTNODE([' + path + ']) AND [cq:styleId] LIKE "%"')
    }

    /**
     * Build
     *
     * Builds the report
     *
     * @return
     */
    def build() {
        def contentNode = session.getNode(contentPath)
        searchNodeForStyles(contentNode)

        getPolicies(policyPath).nodes.each {
            node ->
                processNodeStyleGroups(node)
        }
    }

    /**
     * Process Node Style Groups
     *
     * @param node
     * @return
     */
    private def processNodeStyleGroups(Node node) {
        if (node.hasNode('cq:styleGroups')) {
            QueryResult styleGroups = getStyleGroups(node.path)

            styleGroups.nodes.each {
                groupNode ->
                    processStyleGroupStyles(node, groupNode)
            }
        }
    }

    /**
     * Process Style Group Styles
     *
     * @param groupNode
     * @return
     */
    private def processStyleGroupStyles(Node node, Node groupNode) {
        QueryResult styles = getStyles(groupNode.path)
        styles.nodes.each {
            styleNode ->
                String styleId = styleNode.getProperty("cq:styleId").getValue().toString()
                stylesList.add(new Style(
                        componentName: node.getParent().getName(),
                        policyName: node.getProperty("jcr:title").getValue().toString(),
                        styleGroup: groupNode.getProperty("cq:styleGroupLabel").getValue().toString(),
                        styleId: styleId,
                        styleName: styleNode.getProperty("cq:styleLabel").getValue().toString(),
                        cssClasses: styleNode.getProperty("cq:styleClasses").getValue().toString(),
                        count: stylesCount.getOrDefault(styleId, 0)
                ))
        }
    }

    /**
     * Get Report
     *
     * @return
     */
    ArrayList getReport() {
        return stylesList
    }
}

// Build the Report!
report = new Report(session: session, contentPath: "/content", policyPath: "/conf")
report.build()

// Output Report
report.getReport().forEach {
    style ->
        println style.toCsvString()
}
